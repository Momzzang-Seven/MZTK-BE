package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePostService unit test (T1 saver)")
class CreatePostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LinkTagPort linkTagPort;
  @Mock private ValidatePostImagesPort validatePostImagesPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;

  private CreatePostService createPostService;

  @BeforeEach
  void setUp() {
    createPostService =
        new CreatePostService(
            postPersistencePort, linkTagPort, validatePostImagesPort, updatePostImagesPort);
  }

  @Test
  @DisplayName("saves post, syncs images, links tags, and returns the new post id")
  void createFreePostSyncsImagesAndTags() {
    CreatePostCommand command =
        CreatePostCommand.of(
            7L, null, "content", PostType.FREE, 0L, List.of(1L, 2L), List.of("java", "spring"));

    Post savedPost =
        Post.builder()
            .id(10L)
            .userId(7L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .tags(List.of("java", "spring"))
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);

    Long postId = createPostService.createFreePost(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getReward()).isEqualTo(0L);

    verify(validatePostImagesPort)
        .validateAttachableImages(7L, null, PostType.FREE, List.of(1L, 2L));
    verify(updatePostImagesPort).updateImages(7L, 10L, PostType.FREE, List.of(1L, 2L));
    verify(linkTagPort).linkTagsToPost(10L, List.of("java", "spring"));
    assertThat(postId).isEqualTo(10L);
  }

  @Test
  @DisplayName("skips image sync and tag link when both are empty")
  void createFreePostWithoutTagsSkipsOrchestration() {
    CreatePostCommand command =
        CreatePostCommand.of(1L, null, "content", PostType.FREE, 0L, null, List.of());

    Post savedPost =
        Post.builder()
            .id(11L)
            .userId(1L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);

    Long postId = createPostService.createFreePost(command);

    verifyNoInteractions(validatePostImagesPort);
    verify(updatePostImagesPort, never()).updateImages(any(), any(), any(), any());
    verify(linkTagPort, never()).linkTagsToPost(any(), any());
    assertThat(postId).isEqualTo(11L);
  }

  @Test
  @DisplayName("empty imageIds skips image sync but still links tags")
  void createFreePostWithEmptyImageIdsSkipsImageSync() {
    CreatePostCommand command =
        CreatePostCommand.of(2L, null, "content", PostType.FREE, 0L, List.of(), List.of("java"));

    Post savedPost =
        Post.builder()
            .id(13L)
            .userId(2L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);

    createPostService.createFreePost(command);

    verifyNoInteractions(validatePostImagesPort);
    verify(updatePostImagesPort, never()).updateImages(any(), any(), any(), any());
    verify(linkTagPort).linkTagsToPost(13L, List.of("java"));
  }

  @Test
  @DisplayName("rejects invalid command before persistence")
  void createFreePostRejectsInvalidCommand() {
    CreatePostCommand command = CreatePostCommand.of(1L, null, " ", PostType.FREE, 0L, null, null);

    assertThatThrownBy(() -> createPostService.createFreePost(command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(
        postPersistencePort, linkTagPort, validatePostImagesPort, updatePostImagesPort);
  }

  @Test
  @DisplayName("rejects question command because free-create service keeps free posts only")
  void createFreePostRejectsQuestionCommand() {
    CreatePostCommand command =
        CreatePostCommand.of(
            3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, List.of(1L, 2L), List.of("java"));

    assertThatThrownBy(() -> createPostService.createFreePost(command))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("free posts only");

    verifyNoInteractions(
        postPersistencePort, linkTagPort, validatePostImagesPort, updatePostImagesPort);
  }
}
