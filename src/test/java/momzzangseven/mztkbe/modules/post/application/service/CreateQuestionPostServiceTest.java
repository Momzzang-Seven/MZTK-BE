package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostPublicationStateException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PublishPostDeletedEventPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateQuestionPostService unit test")
class CreateQuestionPostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LinkTagPort linkTagPort;
  @Mock private ValidatePostImagesPort validatePostImagesPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;
  @Mock private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
  @Mock private PublishPostDeletedEventPort publishPostDeletedEventPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private CreateQuestionPostService createQuestionPostService;

  @BeforeEach
  void setUp() {
    createQuestionPostService =
        new CreateQuestionPostService(
            postPersistencePort,
            linkTagPort,
            validatePostImagesPort,
            updatePostImagesPort,
            questionLifecycleExecutionPort,
            publishPostDeletedEventPort,
            eventPublisher,
            ZoneId.of("Asia/Seoul"));
  }

  @Test
  @DisplayName("cleans up unbound pending question when managed Web3 returns no create intent")
  void executeCleansUpPendingQuestionWhenManagedWeb3ReturnsNoCreateIntent() {
    CreatePostCommand command =
        CreatePostCommand.of(3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, null, null);
    Post savedPost =
        Post.builder()
            .id(21L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.PENDING)
            .build();

    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postPersistencePort.loadPostForUpdate(21L)).thenReturn(Optional.of(savedPost));
    when(questionLifecycleExecutionPort.prepareQuestionCreate(21L, 3L, "질문 내용", 50L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> createQuestionPostService.execute(command))
        .isInstanceOf(PostPublicationStateException.class);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.PENDING);
    verify(postPersistencePort).deletePost(savedPost);
    verify(publishPostDeletedEventPort).publish(new PostDeletedEvent(21L, PostType.QUESTION));
  }

  @Test
  @DisplayName(
      "binds prepared create intent id with expected-state CAS after managed prepare succeeds")
  void executeBindsPreparedCreateIntentIdWithExpectedState() {
    CreatePostCommand command =
        CreatePostCommand.of(3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, null, null);
    Post savedPost =
        Post.builder()
            .id(21L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.PENDING)
            .build();

    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(questionLifecycleExecutionPort.prepareQuestionCreate(21L, 3L, "질문 내용", 50L))
        .thenReturn(Optional.of(web3("intent-1")));
    when(postPersistencePort.updateQuestionPublicationStateIfExpected(
            21L,
            PostPublicationStatus.PENDING,
            null,
            null,
            null,
            PostPublicationStatus.PENDING,
            "intent-1",
            null,
            null))
        .thenReturn(1);

    createQuestionPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.PENDING);
    verify(postPersistencePort)
        .updateQuestionPublicationStateIfExpected(
            21L,
            PostPublicationStatus.PENDING,
            null,
            null,
            null,
            PostPublicationStatus.PENDING,
            "intent-1",
            null,
            null);
  }

  @Test
  @DisplayName("cleans up unbound pending question when managed Web3 prepare fails")
  void executeCleansUpPendingQuestionWhenManagedWeb3PrepareFails() {
    CreatePostCommand command =
        CreatePostCommand.of(3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, null, null);
    Post savedPost =
        Post.builder()
            .id(21L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.PENDING)
            .build();
    RuntimeException failure = new RuntimeException("web3 unavailable");

    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(questionLifecycleExecutionPort.prepareQuestionCreate(21L, 3L, "질문 내용", 50L))
        .thenThrow(failure);
    when(postPersistencePort.loadPostForUpdate(21L)).thenReturn(Optional.of(savedPost));

    assertThatThrownBy(() -> createQuestionPostService.execute(command)).isSameAs(failure);

    verify(postPersistencePort).deletePost(savedPost);
    verify(publishPostDeletedEventPort).publish(new PostDeletedEvent(21L, PostType.QUESTION));
  }

  @Test
  @DisplayName("cancels newly created signable intent and cleans up when bind misses")
  void executeCancelsNewIntentAndCleansUpWhenBindMisses() {
    CreatePostCommand command =
        CreatePostCommand.of(3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, null, null);
    Post savedPost =
        Post.builder()
            .id(21L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.PENDING)
            .build();

    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(questionLifecycleExecutionPort.prepareQuestionCreate(21L, 3L, "질문 내용", 50L))
        .thenReturn(Optional.of(web3("intent-1")));
    when(postPersistencePort.updateQuestionPublicationStateIfExpected(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);
    when(postPersistencePort.loadPostForUpdate(21L)).thenReturn(Optional.of(savedPost));

    assertThatThrownBy(() -> createQuestionPostService.execute(command))
        .isInstanceOf(PostPublicationStateException.class);

    verify(questionLifecycleExecutionPort)
        .cancelSignableIntent("intent-1", "question create intent bind failed");
    verify(postPersistencePort).deletePost(savedPost);
    verify(publishPostDeletedEventPort).publish(new PostDeletedEvent(21L, PostType.QUESTION));
  }

  @Test
  @DisplayName("does not cancel reused existing intent when bind misses")
  void executeDoesNotCancelExistingIntentWhenBindMisses() {
    CreatePostCommand command =
        CreatePostCommand.of(3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, null, null);
    Post savedPost =
        Post.builder()
            .id(21L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.PENDING)
            .build();

    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(questionLifecycleExecutionPort.prepareQuestionCreate(21L, 3L, "질문 내용", 50L))
        .thenReturn(Optional.of(web3("intent-existing", true)));
    when(postPersistencePort.updateQuestionPublicationStateIfExpected(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);
    when(postPersistencePort.loadPostForUpdate(21L)).thenReturn(Optional.of(savedPost));

    assertThatThrownBy(() -> createQuestionPostService.execute(command))
        .isInstanceOf(PostPublicationStateException.class);

    verify(questionLifecycleExecutionPort, org.mockito.Mockito.never())
        .cancelSignableIntent(any(), any());
  }

  @Test
  @DisplayName(
      "creates question post, performs precheck, prepares web3 execution, and publishes"
          + " PostCreatedEvent")
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
    when(questionLifecycleExecutionPort.prepareQuestionCreate(20L, 3L, "질문 내용", 50L))
        .thenReturn(Optional.empty());

    CreateQuestionPostResult result = createQuestionPostService.execute(command);

    InOrder inOrder =
        org.mockito.Mockito.inOrder(validatePostImagesPort, questionLifecycleExecutionPort);
    inOrder
        .verify(validatePostImagesPort)
        .validateAttachableImages(3L, null, PostType.QUESTION, List.of(1L, 2L));
    inOrder.verify(questionLifecycleExecutionPort).precheckQuestionCreate(3L, 50L);
    verify(questionLifecycleExecutionPort).precheckQuestionCreate(3L, 50L);
    verify(updatePostImagesPort).updateImages(3L, 20L, PostType.QUESTION, List.of(1L, 2L));
    verify(linkTagPort).linkTagsToPost(20L, List.of("java"));
    verify(questionLifecycleExecutionPort).prepareQuestionCreate(20L, 3L, "질문 내용", 50L);

    ArgumentCaptor<PostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PostCreatedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().userId()).isEqualTo(3L);
    assertThat(eventCaptor.getValue().postId()).isEqualTo(20L);
    assertThat(eventCaptor.getValue().type()).isEqualTo(PostType.QUESTION);

    // XP is granted asynchronously by the level module on AFTER_COMMIT, so the response no longer
    // reports XP (FE consumes only postId/web3 from the question-create response).
    assertThat(result.postId()).isEqualTo(20L);
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isEqualTo(30);
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
    assertThat(result.web3()).isNull();
  }

  @Test
  @DisplayName(
      "rejects free command because question-create service is dedicated to question board")
  void executeRejectsFreeCommand() {
    CreatePostCommand command = CreatePostCommand.of(1L, null, "본문", PostType.FREE, 0L, null, null);

    assertThatThrownBy(() -> createQuestionPostService.execute(command))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("question posts only");

    verifyNoInteractions(
        postPersistencePort,
        eventPublisher,
        linkTagPort,
        validatePostImagesPort,
        updatePostImagesPort,
        questionLifecycleExecutionPort,
        publishPostDeletedEventPort);
  }

  private momzzangseven.mztkbe.modules.post.application.dto.QuestionExecutionWriteView web3(
      String executionIntentId) {
    return web3(executionIntentId, false);
  }

  private momzzangseven.mztkbe.modules.post.application.dto.QuestionExecutionWriteView web3(
      String executionIntentId, boolean existing) {
    return new momzzangseven.mztkbe.modules.post.application.dto.QuestionExecutionWriteView(
        null,
        "QNA_QUESTION_CREATE",
        new momzzangseven.mztkbe.modules.post.application.dto.QuestionExecutionWriteView
            .ExecutionIntent(executionIntentId, "AWAITING_SIGNATURE", null, 1L),
        null,
        null,
        null,
        existing);
  }
}
