package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QuestionUpdateStatePreparationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.BeginQuestionUpdateStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionLifecycleExecutionAdapter unit test")
class QuestionLifecycleExecutionAdapterTest {

  @Mock private QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;
  @Mock private BeginQuestionUpdateStateUseCase beginQuestionUpdateStateUseCase;
  @Mock private CancelExecutionIntentUseCase cancelExecutionIntentUseCase;
  @Mock private GetExecutionIntentUseCase getExecutionIntentUseCase;

  @InjectMocks private QuestionLifecycleExecutionAdapter adapter;

  @Test
  @DisplayName("precheckQuestionCreate delegates to the qna use case with mapped command")
  void precheckQuestionCreate_delegates() {
    adapter.precheckQuestionCreate(7L, 50L);

    verify(questionEscrowExecutionUseCase)
        .precheckQuestionCreate(new PrecheckQuestionCreateCommand(7L, 50L));
  }

  @Test
  @DisplayName("prepareQuestionCreate delegates to the qna use case with mapped command")
  void prepareQuestionCreate_delegates() {
    given(questionEscrowExecutionUseCase.prepareQuestionCreate(any()))
        .willReturn(questionIntent("QNA_QUESTION_CREATE", "intent-create"));

    var result = adapter.prepareQuestionCreate(10L, 7L, "질문 내용", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionCreate(new PrepareQuestionCreateCommand(10L, 7L, "질문 내용", 50L));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_QUESTION_CREATE");
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-create");
  }

  @Test
  @DisplayName("cancelSignableIntent delegates to execution cancellation use case")
  void cancelSignableIntent_delegates() {
    given(cancelExecutionIntentUseCase.cancelIfSignable(any())).willReturn(true);

    boolean result = adapter.cancelSignableIntent("intent-create", "question create bind failed");

    assertThat(result).isTrue();
    verify(cancelExecutionIntentUseCase)
        .cancelIfSignable(
            new CancelExecutionIntentCommand(
                "intent-create", "QUESTION_LIFECYCLE_BIND_FAILED", "question create bind failed"));
  }

  @Test
  @DisplayName("loadQuestionCreateIntent restores owner-bound execution intent")
  void loadQuestionCreateIntent_delegates() {
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.QUESTION,
                "10",
                ExecutionResourceStatus.PENDING_EXECUTION,
                "intent-create",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 4, 14, 10, 0),
                ExecutionMode.EIP7702,
                2,
                null,
                null,
                null,
                null));

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_QUESTION_CREATE");
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-create");
    verify(getExecutionIntentUseCase).execute(new GetExecutionIntentQuery(7L, "intent-create"));
  }

  @Test
  @DisplayName("loadQuestionCreateIntent rejects non-question resource")
  void loadQuestionCreateIntentRejectsNonQuestionResource() {
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.ANSWER,
                "10",
                ExecutionResourceStatus.PENDING_EXECUTION,
                "intent-create",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 4, 14, 10, 0),
                ExecutionMode.EIP7702,
                2,
                null,
                null,
                null,
                null));

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("beginQuestionUpdateState delegates with hashed content")
  void beginQuestionUpdateState_delegates() {
    String hash = QnaContentHashFactory.hash("수정된 질문 내용");
    given(beginQuestionUpdateStateUseCase.begin(any()))
        .willReturn(new QuestionUpdateStatePreparationResult(10L, 1L, "token", hash));

    var result = adapter.beginQuestionUpdateState(10L, 7L, "수정된 질문 내용");

    verify(beginQuestionUpdateStateUseCase)
        .begin(new BeginQuestionUpdateStateCommand(10L, 7L, hash));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().updateVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("prepareQuestionUpdate delegates to the qna use case with mapped command")
  void prepareQuestionUpdate_delegates() {
    given(questionEscrowExecutionUseCase.prepareQuestionUpdate(any()))
        .willReturn(questionIntent("QNA_QUESTION_UPDATE", "intent-update"));

    var result = adapter.prepareQuestionUpdate(10L, 7L, "수정된 질문 내용", 50L, 1L, "token");

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionUpdate(
            new PrepareQuestionUpdateCommand(10L, 7L, "수정된 질문 내용", 50L, 1L, "token"));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resource().type()).isEqualTo("QUESTION");
  }

  @Test
  @DisplayName("prepareQuestionDelete delegates to the qna use case with mapped command")
  void prepareQuestionDelete_delegates() {
    given(questionEscrowExecutionUseCase.prepareQuestionDelete(any()))
        .willReturn(questionIntent("QNA_QUESTION_DELETE", "intent-delete"));

    var result = adapter.prepareQuestionDelete(10L, 7L, "삭제될 질문", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionDelete(new PrepareQuestionDeleteCommand(10L, 7L, "삭제될 질문", 50L));
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("prepareAnswerAccept delegates to the qna use case with mapped command")
  void prepareAnswerAccept_delegates() {
    given(questionEscrowExecutionUseCase.prepareAnswerAccept(any()))
        .willReturn(questionIntent("QNA_ANSWER_ACCEPT", "intent-accept"));

    var result = adapter.prepareAnswerAccept(10L, 20L, 7L, 8L, "질문 내용", "답변 내용", 100L);

    verify(questionEscrowExecutionUseCase)
        .prepareAnswerAccept(
            new PrepareAnswerAcceptCommand(10L, 20L, 7L, 8L, "질문 내용", "답변 내용", 100L));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().execution().mode()).isEqualTo("EIP7702");
  }

  private QnaExecutionIntentResult questionIntent(String actionType, String intentId) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "10", "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionIntentResult.Execution("EIP7702", 2),
        null,
        false);
  }
}
