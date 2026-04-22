package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrepareQnaAdminSettlementService 단위 테스트")
class PrepareQnaAdminSettlementServiceTest {

  @Mock private QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService;

  @InjectMocks private PrepareQnaAdminSettlementService service;

  @Test
  @DisplayName("execute는 admin settlement 준비를 delegate에 위임한다")
  void execute_delegatesToAdminSettlementPreparation() {
    PrepareAdminSettleCommand command =
        new PrepareAdminSettleCommand(101L, 201L, 7L, 22L, "question", "answer");
    QnaExecutionIntentResult expected = executionIntentResult("QNA_ADMIN_SETTLE");
    when(questionEscrowAdminExecutionService.prepareAdminSettle(command)).thenReturn(expected);

    QnaExecutionIntentResult actual = service.execute(command);

    assertThat(actual).isEqualTo(expected);
    verify(questionEscrowAdminExecutionService).prepareAdminSettle(command);
  }

  private QnaExecutionIntentResult executionIntentResult(String actionType) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "101", "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 20, 12, 0)),
        new QnaExecutionIntentResult.Execution("EIP1559", 1),
        null,
        false);
  }
}
