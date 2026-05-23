package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.Test;

class QnaExecutionResumeViewMapperTest {

  @Test
  void toResult_whenTransactionUnconfirmed_exposesOnchainUncertainHint() {
    QnaExecutionResumeViewResult result =
        QnaExecutionResumeViewMapper.toResult(
            QnaExecutionResourceType.QUESTION, summary(ExecutionTransactionStatus.UNCONFIRMED));

    assertThat(result.resource().status().name()).isEqualTo("PENDING_EXECUTION");
    assertThat(result.transaction().status()).isEqualTo("UNCONFIRMED");
    assertThat(result.recoveryStatus()).isEqualTo("ONCHAIN_UNCERTAIN");
    assertThat(result.recoveryReason()).isEqualTo("RECEIPT_TIMEOUT");
    assertThat(result.retryAllowed()).isFalse();
  }

  @Test
  void toResult_whenTransactionPending_doesNotExposeRecoveryHint() {
    QnaExecutionResumeViewResult result =
        QnaExecutionResumeViewMapper.toResult(
            QnaExecutionResourceType.QUESTION, summary(ExecutionTransactionStatus.PENDING));

    assertThat(result.recoveryStatus()).isNull();
    assertThat(result.recoveryReason()).isNull();
    assertThat(result.retryAllowed()).isNull();
  }

  private static GetLatestExecutionIntentSummaryResult summary(
      ExecutionTransactionStatus transactionStatus) {
    return new GetLatestExecutionIntentSummaryResult(
        ExecutionResourceType.QUESTION,
        "101",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.QNA_QUESTION_UPDATE,
        "intent-1",
        ExecutionIntentStatus.PENDING_ONCHAIN,
        LocalDateTime.parse("2026-05-13T10:05:00"),
        1L,
        ExecutionMode.EIP7702,
        2,
        10L,
        transactionStatus,
        "0x" + "a".repeat(64));
  }
}
