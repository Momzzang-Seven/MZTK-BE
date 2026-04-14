package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerLifecycleExecutionAdapter unit test")
class AnswerLifecycleExecutionAdapterTest {

  @Mock private AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase;

  @InjectMocks private AnswerLifecycleExecutionAdapter adapter;

  @Test
  @DisplayName("prepareAnswerCreate delegates to the qna use case with mapped command")
  void prepareAnswerCreate_delegates() {
    given(answerEscrowExecutionUseCase.prepareAnswerCreate(any()))
        .willReturn(answerIntent("QNA_ANSWER_SUBMIT", "intent-create"));

    var result = adapter.prepareAnswerCreate(10L, 20L, 7L, 8L, "질문 내용", 50L, "답변 내용", 1);

    verify(answerEscrowExecutionUseCase)
        .prepareAnswerCreate(
            new PrepareAnswerCreateCommand(10L, 20L, 7L, 8L, "질문 내용", 50L, "답변 내용", 1));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resource().type()).isEqualTo("ANSWER");
  }

  @Test
  @DisplayName("prepareAnswerUpdate delegates to the qna use case with mapped command")
  void prepareAnswerUpdate_delegates() {
    given(answerEscrowExecutionUseCase.prepareAnswerUpdate(any()))
        .willReturn(answerIntent("QNA_ANSWER_UPDATE", "intent-update"));

    var result = adapter.prepareAnswerUpdate(10L, 20L, 7L, 8L, "질문 내용", 50L, "수정된 답변", 2);

    verify(answerEscrowExecutionUseCase)
        .prepareAnswerUpdate(
            new PrepareAnswerUpdateCommand(10L, 20L, 7L, 8L, "질문 내용", 50L, "수정된 답변", 2));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_ANSWER_UPDATE");
  }

  @Test
  @DisplayName("prepareAnswerDelete delegates to the qna use case with mapped command")
  void prepareAnswerDelete_delegates() {
    given(answerEscrowExecutionUseCase.prepareAnswerDelete(any()))
        .willReturn(answerIntent("QNA_ANSWER_DELETE", "intent-delete"));

    var result = adapter.prepareAnswerDelete(10L, 20L, 7L, 8L, "질문 내용", 50L, 0);

    verify(answerEscrowExecutionUseCase)
        .prepareAnswerDelete(new PrepareAnswerDeleteCommand(10L, 20L, 7L, 8L, "질문 내용", 50L, 0));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-delete");
  }

  private QnaExecutionIntentResult answerIntent(String actionType, String intentId) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("ANSWER", "20", "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionIntentResult.Execution("EIP7702", 2),
        null,
        false);
  }
}
