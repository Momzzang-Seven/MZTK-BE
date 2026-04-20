package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrepareQnaAdminRefundService 단위 테스트")
class PrepareQnaAdminRefundServiceTest {

  @Mock private QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService;

  @InjectMocks private PrepareQnaAdminRefundService service;

  @Test
  @DisplayName("execute는 admin refund 준비를 delegate에 위임한다")
  void execute_delegatesToAdminRefundPreparation() {
    PrepareAdminRefundCommand command = new PrepareAdminRefundCommand(101L, 7L);
    QnaExecutionIntentResult expected = executionIntentResult("QNA_ADMIN_REFUND");
    when(questionEscrowAdminExecutionService.prepareAdminRefund(command)).thenReturn(expected);

    QnaExecutionIntentResult actual = service.execute(command);

    assertThat(actual).isEqualTo(expected);
    verify(questionEscrowAdminExecutionService).prepareAdminRefund(command);
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
