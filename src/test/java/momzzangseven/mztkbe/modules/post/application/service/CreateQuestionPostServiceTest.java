package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateQuestionPostService unit test")
class CreateQuestionPostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private PostXpService postXpService;
  @Mock private LinkTagPort linkTagPort;
  @Mock private ValidatePostImagesPort validatePostImagesPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;
  @Mock private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  private CreateQuestionPostService createQuestionPostService;

  @BeforeEach
  void setUp() {
    createQuestionPostService =
        new CreateQuestionPostService(
            postPersistencePort,
            postXpService,
            linkTagPort,
            validatePostImagesPort,
            updatePostImagesPort,
            questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("stores question as pending when Web3 manages question create lifecycle")
  void executeStoresPendingWhenWeb3ManagesQuestionCreate() {
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
        .thenReturn(Optional.empty());

    createQuestionPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.PENDING);
  }

  @Test
  @DisplayName("records prepared create intent id after managed web3 prepare succeeds")
  void executeRecordsPreparedCreateIntentId() {
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
        .thenReturn(Optional.of(web3("intent-1")));

    createQuestionPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort, org.mockito.Mockito.times(2)).savePost(postCaptor.capture());
    assertThat(postCaptor.getAllValues().get(1).getCurrentCreateExecutionIntentId())
        .isEqualTo("intent-1");
  }

  @Test
  @DisplayName(
      "creates question post, performs precheck, prepares web3 execution, and preserves XP fields")
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
    when(postXpService.grantCreatePostXp(3L, 20L)).thenReturn(20L);

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
    assertThat(result.postId()).isEqualTo(20L);
    assertThat(result.isXpGranted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(20L);
    assertThat(result.message()).contains("+20 XP");
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
        postXpService,
        linkTagPort,
        validatePostImagesPort,
        updatePostImagesPort,
        questionLifecycleExecutionPort);
  }

  private momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView web3(
      String executionIntentId) {
    return new momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView(
        null,
        "QNA_QUESTION_CREATE",
        new momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView
            .ExecutionIntent(executionIntentId, "AWAITING_SIGNATURE", null),
        null,
        null,
        false);
  }
}
