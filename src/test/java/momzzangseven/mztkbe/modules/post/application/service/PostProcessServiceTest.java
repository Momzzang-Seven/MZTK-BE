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
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
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
  @Mock private ValidatePostImagesPort validatePostImagesPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;
  @Mock private CountAnswersPort countAnswersPort;
  @Mock private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

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
    verify(validatePostImagesPort)
        .validateAttachableImages(ownerId, postId, post.getType(), List.of(1L));
    verify(updatePostImagesPort).updateImages(ownerId, postId, post.getType(), List.of(1L));
    verifyNoInteractions(questionLifecycleExecutionPort);
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

    verifyNoInteractions(validatePostImagesPort);
    verify(postPersistencePort).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(linkTagPort, never()).updateTags(postId, null);
    verify(updatePostImagesPort, never()).updateImages(ownerId, postId, post.getType(), null);
    verifyNoInteractions(questionLifecycleExecutionPort);
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

    verifyNoInteractions(validatePostImagesPort);
    verify(postPersistencePort).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(updatePostImagesPort).updateImages(ownerId, postId, post.getType(), List.of());
    verifyNoInteractions(questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("update rejects invalid command before loading post")
  void updatePostRejectsInvalidCommand() {
    UpdatePostCommand command = UpdatePostCommand.of(null, null, null, null);

    assertThatThrownBy(() -> postProcessService.updatePost(1L, 1L, command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(
        postPersistencePort,
        linkTagPort,
        validatePostImagesPort,
        updatePostImagesPort,
        questionLifecycleExecutionPort);
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
    verifyNoInteractions(
        linkTagPort, validatePostImagesPort, updatePostImagesPort, questionLifecycleExecutionPort);
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
    verifyNoInteractions(questionLifecycleExecutionPort);
    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(eventCaptor.getValue())
        .isEqualTo(new PostDeletedEvent(postId, PostType.FREE));
  }

  @Test
  @DisplayName("delete throws when post is missing")
  void deletePostThrowsWhenNotFound() {
    when(postPersistencePort.loadPost(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postProcessService.deletePost(1L, 999L))
        .isInstanceOf(PostNotFoundException.class);

    verify(postPersistencePort, never()).deletePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(eventPublisher, questionLifecycleExecutionPort);
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
    verifyNoInteractions(eventPublisher, questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("answered QUESTION posts cannot be updated")
  void updateAnsweredQuestionPostThrows() {
    Long ownerId = 7L;
    Long postId = 70L;
    Post post = questionPost(ownerId, postId);
    UpdatePostCommand command = UpdatePostCommand.of("edited title", null, null, null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(1L);

    assertThatThrownBy(() -> postProcessService.updatePost(ownerId, postId, command))
        .isInstanceOf(PostInvalidInputException.class);

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(
        linkTagPort, validatePostImagesPort, updatePostImagesPort, questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("answered QUESTION posts cannot be deleted")
  void deleteAnsweredQuestionPostThrows() {
    Long ownerId = 7L;
    Long postId = 71L;
    Post post = questionPost(ownerId, postId);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(2L);

    assertThatThrownBy(() -> postProcessService.deletePost(ownerId, postId))
        .isInstanceOf(PostInvalidInputException.class);

    verify(postPersistencePort, never()).deletePost(org.mockito.ArgumentMatchers.any(Post.class));
    verifyNoInteractions(eventPublisher, questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("unanswered QUESTION post can be updated when answer count is zero")
  void updateQuestionPostWhenNoAnswersSucceeds() {
    Long ownerId = 7L;
    Long postId = 72L;
    Post post = questionPost(ownerId, postId);
    UpdatePostCommand command =
        UpdatePostCommand.of("edited title", "수정된 질문 내용", null, List.of("java"));

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);

    postProcessService.updatePost(ownerId, postId, command);

    verify(postPersistencePort).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(linkTagPort).updateTags(postId, List.of("java"));
    verify(questionLifecycleExecutionPort).prepareQuestionUpdate(postId, ownerId, "수정된 질문 내용", 50L);
  }

  @Test
  @DisplayName(
      "QUESTION post update attempts recovery when content is unchanged but on-chain sync may be stale")
  void updateQuestionPostAttemptsRecoveryWhenContentUnchanged() {
    Long ownerId = 7L;
    Long postId = 74L;
    Post post = questionPost(ownerId, postId);
    UpdatePostCommand command = UpdatePostCommand.of("edited title", "질문 내용", null, null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);
    when(questionLifecycleExecutionPort.recoverQuestionUpdate(postId, ownerId, "질문 내용", 50L))
        .thenReturn(Optional.empty());

    postProcessService.updatePost(ownerId, postId, command);

    verify(postPersistencePort).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(questionLifecycleExecutionPort).recoverQuestionUpdate(postId, ownerId, "질문 내용", 50L);
  }

  @Test
  @DisplayName("QUESTION metadata-only update is blocked while another on-chain intent is active")
  void updateQuestionMetadataOnlyBlockedWhenActiveIntentExists() {
    Long ownerId = 7L;
    Long postId = 76L;
    Post post = questionPost(ownerId, postId);
    UpdatePostCommand command = UpdatePostCommand.of("edited title", null, null, null);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);
    when(questionLifecycleExecutionPort.hasActiveQuestionIntent(postId)).thenReturn(true);

    assertThatThrownBy(() -> postProcessService.updatePost(ownerId, postId, command))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("pending onchain mutation");

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
  }

  @Test
  @DisplayName("QUESTION post delete defers local removal when escrow delete intent is created")
  void deleteQuestionPostDefersLocalRemovalWhenEscrowDeleteIsPrepared() {
    Long ownerId = 7L;
    Long postId = 73L;
    Post post = questionPost(ownerId, postId);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);
    when(questionLifecycleExecutionPort.prepareQuestionDelete(postId, ownerId, "질문 내용", 50L))
        .thenReturn(
            Optional.of(
                org.mockito.Mockito.mock(
                    momzzangseven.mztkbe.modules.post.application.port.out
                        .QuestionExecutionWriteView.class)));

    postProcessService.deletePost(ownerId, postId);

    verify(postPersistencePort, never()).deletePost(post);
    verify(questionLifecycleExecutionPort).prepareQuestionDelete(postId, ownerId, "질문 내용", 50L);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("QUESTION post delete still removes local row when web3 lifecycle is disabled")
  void deleteQuestionPostDeletesLocallyWhenEscrowDeleteIsNotPrepared() {
    Long ownerId = 7L;
    Long postId = 75L;
    Post post = questionPost(ownerId, postId);

    when(postPersistencePort.loadPost(postId)).thenReturn(Optional.of(post));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);
    when(questionLifecycleExecutionPort.prepareQuestionDelete(postId, ownerId, "질문 내용", 50L))
        .thenReturn(Optional.empty());

    postProcessService.deletePost(ownerId, postId);

    verify(postPersistencePort).deletePost(post);
    verify(questionLifecycleExecutionPort).prepareQuestionDelete(postId, ownerId, "질문 내용", 50L);
    verify(eventPublisher).publishEvent(new PostDeletedEvent(postId, PostType.QUESTION));
  }

  private Post questionPost(Long ownerId, Long postId) {
    LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(postId)
        .userId(ownerId)
        .type(PostType.QUESTION)
        .title("질문 제목")
        .content("질문 내용")
        .reward(50L)
        .status(PostStatus.OPEN)
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
        .status(PostStatus.OPEN)
        .tags(List.of("old-tag"))
        .createdAt(updatedAt.minusHours(1))
        .updatedAt(updatedAt)
        .build();
  }
}
