package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.global.error.post.PostUnauthorizedException;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostProcessService unit test")
class PostProcessServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private LinkTagPort linkTagPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;

  @InjectMocks private PostProcessService postProcessService;

  @Test
  @DisplayName("update saves modified post and updates tags when tag list is present")
  void updatePostSuccessWithTags() {
    Long ownerId = 7L;
    Long postId = 50L;
    Post post = ownedPost(ownerId, postId);
    UpdatePostCommand command =
        UpdatePostCommand.of("new title", "new content", List.of(Long.valueOf(1)), List.of("java"));

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    postProcessService.updatePost(ownerId, postId, command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());

    Post saved = postCaptor.getValue();
    assertThat(saved.getTitle()).isEqualTo("new title");
    assertThat(saved.getContent()).isEqualTo("new content");
    assertThat(saved.getTags()).containsExactly("java");
    assertThat(saved.getUpdatedAt()).isAfter(post.getUpdatedAt());

    verify(linkTagPort).updateTags(postId, List.of("java"));
    verify(updatePostImagesPort).updateImages(ownerId, postId, post.getType(), List.of(1L));
  }

  @Test
  @DisplayName("update does not call tag updater when tag list is null")
  void updatePostWithoutTagsSkipsTagUpdate() {
    Long ownerId = 7L;
    Long postId = 51L;
    Post post = ownedPost(ownerId, postId);
    UpdatePostCommand command = UpdatePostCommand.of("only title", null, null, null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    postProcessService.updatePost(ownerId, postId, command);

    verify(postPersistencePort).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(linkTagPort, never()).updateTags(postId, null);
    verify(updatePostImagesPort, never()).updateImages(ownerId, postId, post.getType(), null);
  }

  @Test
  @DisplayName("update with empty imageIds delegates explicit image removal sync")
  void updatePostWithEmptyImageIdsCallsImageSync() {
    Long ownerId = 7L;
    Long postId = 53L;
    Post post = ownedPost(ownerId, postId);
    UpdatePostCommand command = UpdatePostCommand.of(null, "new content", List.of(), null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    postProcessService.updatePost(ownerId, postId, command);

    verify(postPersistencePort).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(updatePostImagesPort).updateImages(ownerId, postId, post.getType(), List.of());
  }

  @Test
  @DisplayName("update rejects invalid command before loading post")
  void updatePostRejectsInvalidCommand() {
    UpdatePostCommand command = UpdatePostCommand.of(null, null, null, null);

    assertThatThrownBy(() -> postProcessService.updatePost(1L, 1L, command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(postPersistencePort, linkTagPort);
  }

  @Test
  @DisplayName("update throws when current user is not owner")
  void updatePostThrowsWhenUnauthorized() {
    Long postId = 52L;
    Post post = ownedPost(7L, postId);
    UpdatePostCommand command = UpdatePostCommand.of("title", null, null, null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> postProcessService.updatePost(8L, postId, command))
        .isInstanceOf(PostUnauthorizedException.class);

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(linkTagPort);
  }

  @Test
  @DisplayName("delete removes post and publishes deletion event")
  void deletePostSuccess() {
    Long ownerId = 11L;
    Long postId = 60L;
    Post post = ownedPost(ownerId, postId);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    postProcessService.deletePost(ownerId, postId);

    verify(postPersistencePort).deletePost(post);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isEqualTo(new PostDeletedEvent(postId, PostType.FREE));
  }

  @Test
  @DisplayName("delete throws when post is missing")
  void deletePostThrowsWhenNotFound() {
    when(postPersistencePort.loadPost(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postProcessService.deletePost(1L, 999L))
        .isInstanceOf(PostNotFoundException.class);

    verify(postPersistencePort, never()).deletePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("delete throws when current user is not owner")
  void deletePostThrowsWhenUnauthorized() {
    Long postId = 61L;
    Post post = ownedPost(5L, postId);
    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> postProcessService.deletePost(6L, postId))
        .isInstanceOf(PostUnauthorizedException.class);

    verify(postPersistencePort, never()).deletePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Resolved QUESTION posts cannot be updated")
  void updateSolvedQuestionPostThrows() {
    Long ownerId = 7L;
    Long postId = 70L;
    Post post = solvedQuestionPost(ownerId, postId);
    UpdatePostCommand command = UpdatePostCommand.of("edited title", null, null, null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> postProcessService.updatePost(ownerId, postId, command))
        .isInstanceOf(PostInvalidInputException.class);

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(linkTagPort);
  }

  @Test
  @DisplayName("Resolved QUESTION posts cannot be deleted")
  void deleteSolvedQuestionPostThrows() {
    Long ownerId = 7L;
    Long postId = 71L;
    Post post = solvedQuestionPost(ownerId, postId);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> postProcessService.deletePost(ownerId, postId))
        .isInstanceOf(PostInvalidInputException.class);

    verify(postPersistencePort, never()).deletePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(eventPublisher);
  }

  private Post solvedQuestionPost(Long ownerId, Long postId) {
    LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(postId)
        .userId(ownerId)
        .type(PostType.QUESTION)
        .title("질문 제목")
        .content("질문 내용")
        .reward(50L)
        .isSolved(true)
        .createdAt(updatedAt.minusHours(1))
        .updatedAt(updatedAt)
        .build();
  }

  private Post ownedPost(Long ownerId, Long postId) {
    LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(postId)
        .userId(ownerId)
        .type(PostType.FREE)
        .title("old title")
        .content("old content")
        .reward(0L)
        .isSolved(false)
        .tags(List.of("old-tag"))
        .createdAt(updatedAt.minusHours(1))
        .updatedAt(updatedAt)
        .build();
  }
}
