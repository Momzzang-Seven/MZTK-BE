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
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePostService unit test")
class CreatePostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private PostXpService postXpService;
  @Mock private LinkTagPort linkTagPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;
  @Mock private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @InjectMocks private CreatePostService createPostService;

  @Test
  @DisplayName("creates post, syncs images, links tags, and includes granted XP message")
  void executeSuccessWithTagsAndXpGranted() {
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
    when(postXpService.grantCreatePostXp(7L, 10L)).thenReturn(30L);

    CreatePostResult result = createPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getReward()).isEqualTo(0L);

    // Verify image sync is called
    verify(updatePostImagesPort).updateImages(7L, 10L, PostType.FREE, List.of(1L, 2L));
    verify(linkTagPort).linkTagsToPost(10L, List.of("java", "spring"));
    verify(postXpService).grantCreatePostXp(7L, 10L);
    verifyNoInteractions(questionLifecycleExecutionPort);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.isXpGranted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(30L);
    assertThat(result.message()).isEqualTo("게시글 작성 완료! (+30 XP)");
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
    when(postXpService.grantCreatePostXp(1L, 11L)).thenReturn(0L);

    CreatePostResult result = createPostService.execute(command);

    verify(updatePostImagesPort, never()).updateImages(any(), any(), any(), any());
    verify(linkTagPort, never()).linkTagsToPost(any(), any());
    verifyNoInteractions(questionLifecycleExecutionPort);
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isZero();
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
    when(postXpService.grantCreatePostXp(2L, 13L)).thenReturn(0L);

    createPostService.execute(command);

    verify(updatePostImagesPort, never()).updateImages(any(), any(), any(), any());
    verify(linkTagPort).linkTagsToPost(13L, List.of("java"));
    verifyNoInteractions(questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("continues when XP service fails but image sync succeeded")
  void executeContinuesWhenXpGrantFails() {
    CreatePostCommand command =
        CreatePostCommand.of(4L, null, "content", PostType.FREE, 0L, List.of(1L), List.of("java"));

    Post savedPost =
        Post.builder()
            .id(12L)
            .userId(4L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postXpService.grantCreatePostXp(4L, 12L)).thenThrow(new RuntimeException("xp failed"));

    CreatePostResult result = createPostService.execute(command);

    verify(updatePostImagesPort).updateImages(4L, 12L, PostType.FREE, List.of(1L));
    verify(linkTagPort).linkTagsToPost(12L, List.of("java"));
    verifyNoInteractions(questionLifecycleExecutionPort);
    assertThat(result.postId()).isEqualTo(12L);
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
  }

  @Test
  @DisplayName("rejects invalid command before persistence")
  void executeRejectsInvalidCommand() {
    CreatePostCommand command = CreatePostCommand.of(1L, null, " ", PostType.FREE, 0L, null, null);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(
        postPersistencePort,
        postXpService,
        linkTagPort,
        updatePostImagesPort,
        questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("QUESTION 게시글 생성 성공 - title, reward 포함 및 이미지 sync")
  void executeSuccessWithQuestionPost() {
    CreatePostCommand command =
        CreatePostCommand.of(
            3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, List.of(1L, 2L), List.of("java"));

    Post savedPost =
        Post.builder()
            .id(20L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .status(PostStatus.OPEN)
            .tags(List.of("java"))
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postXpService.grantCreatePostXp(3L, 20L)).thenReturn(0L);

    CreatePostResult result = createPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    Post captured = postCaptor.getValue();
    assertThat(captured.getType()).isEqualTo(PostType.QUESTION);
    assertThat(captured.getTitle()).isEqualTo("질문 제목");
    assertThat(captured.getReward()).isEqualTo(50L);
    assertThat(captured.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(captured.getAcceptedAnswerId()).isNull();

    // Verify image sync for QUESTION type
    verify(updatePostImagesPort).updateImages(3L, 20L, PostType.QUESTION, List.of(1L, 2L));
    verify(linkTagPort).linkTagsToPost(20L, List.of("java"));
    verify(questionLifecycleExecutionPort).precheckQuestionCreate(3L, 50L);
    verify(questionLifecycleExecutionPort).prepareQuestionCreate(20L, 3L, "질문 내용", 50L);
    assertThat(result.postId()).isEqualTo(20L);
  }

  @Test
  @DisplayName("question post with zero reward is blocked by command validation")
  void executeRejectsQuestionWithZeroReward() {
    CreatePostCommand command =
        CreatePostCommand.of(2L, "title", "content", PostType.QUESTION, 0L, null, null);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(momzzangseven.mztkbe.global.error.post.PostInvalidInputException.class)
        .hasMessageContaining("Questions must have a valid reward");

    verifyNoInteractions(
        postPersistencePort,
        postXpService,
        linkTagPort,
        updatePostImagesPort,
        questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("question create precheck failure prevents persistence")
  void executeQuestionPrecheckFailureStopsBeforeSave() {
    CreatePostCommand command =
        CreatePostCommand.of(9L, "질문", "질문 내용", PostType.QUESTION, 10L, null, null);

    org.mockito.Mockito.doThrow(new RuntimeException("allowance 부족"))
        .when(questionLifecycleExecutionPort)
        .precheckQuestionCreate(9L, 10L);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("allowance 부족");

    verify(questionLifecycleExecutionPort).precheckQuestionCreate(9L, 10L);
    verifyNoInteractions(postPersistencePort, postXpService, linkTagPort, updatePostImagesPort);
  }
}
