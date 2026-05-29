package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePostService unit test")
class CreatePostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LinkTagPort linkTagPort;
  @Mock private ValidatePostImagesPort validatePostImagesPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private CreatePostService createPostService;

  @BeforeEach
  void setUp() {
    createPostService =
        new CreatePostService(
            postPersistencePort,
            linkTagPort,
            validatePostImagesPort,
            updatePostImagesPort,
            eventPublisher,
            ZoneId.of("Asia/Seoul"));
  }

  @Test
  @DisplayName("creates post, syncs images, links tags, and publishes PostCreatedEvent")
  void executeSuccessWithTagsAndEventPublished() {
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

    CreatePostResult result = createPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getReward()).isEqualTo(0L);

    verify(validatePostImagesPort)
        .validateAttachableImages(7L, null, PostType.FREE, List.of(1L, 2L));
    // Verify image sync is called
    verify(updatePostImagesPort).updateImages(7L, 10L, PostType.FREE, List.of(1L, 2L));
    verify(linkTagPort).linkTagsToPost(10L, List.of("java", "spring"));

    ArgumentCaptor<PostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PostCreatedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().userId()).isEqualTo(7L);
    assertThat(eventCaptor.getValue().postId()).isEqualTo(10L);
    assertThat(eventCaptor.getValue().type()).isEqualTo(PostType.FREE);

    // XP is granted asynchronously by the level module on AFTER_COMMIT, so the create response no
    // longer reports XP (FE does not consume these fields for posts).
    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isEqualTo(30);
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
  }

  @Test
  @DisplayName("returns plain success message when tags are empty and image sync is skipped")
  void executeSuccessWithoutTagsAndNoXpGrant() {
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

    CreatePostResult result = createPostService.execute(command);

    verifyNoInteractions(validatePostImagesPort);
    verify(updatePostImagesPort, never()).updateImages(any(), any(), any(), any());
    verify(linkTagPort, never()).linkTagsToPost(any(), any());
    verify(eventPublisher).publishEvent(any(PostCreatedEvent.class));
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isEqualTo(30);
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
  }

  @Test
  @DisplayName("empty imageIds skips image sync but still links tags")
  void executeSuccessWithEmptyImageIdsSkipsImageSync() {
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

    createPostService.execute(command);

    verifyNoInteractions(validatePostImagesPort);
    verify(updatePostImagesPort, never()).updateImages(any(), any(), any(), any());
    verify(linkTagPort).linkTagsToPost(13L, List.of("java"));
    verify(eventPublisher).publishEvent(any(PostCreatedEvent.class));
  }

  @Test
  @DisplayName("rejects invalid command before persistence")
  void executeRejectsInvalidCommand() {
    CreatePostCommand command = CreatePostCommand.of(1L, null, " ", PostType.FREE, 0L, null, null);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(
        postPersistencePort,
        eventPublisher,
        linkTagPort,
        validatePostImagesPort,
        updatePostImagesPort);
  }

  @Test
  @DisplayName("rejects question command because free-create service keeps legacy contract only")
  void executeRejectsQuestionCommand() {
    CreatePostCommand command =
        CreatePostCommand.of(
            3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, List.of(1L, 2L), List.of("java"));

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("free posts only");

    verifyNoInteractions(
        postPersistencePort,
        eventPublisher,
        linkTagPort,
        validatePostImagesPort,
        updatePostImagesPort);
  }

  @Test
  @DisplayName("question post with zero reward is blocked by command validation")
  void executeRejectsQuestionWithZeroReward() {
    CreatePostCommand command =
        CreatePostCommand.of(2L, "title", "content", PostType.QUESTION, 0L, null, null);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(
        postPersistencePort,
        eventPublisher,
        linkTagPort,
        validatePostImagesPort,
        updatePostImagesPort);
  }
}
