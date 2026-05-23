package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.time.LocalDateTime;

public record ExecutionTerminationEvidenceView(
    String txHash,
    boolean hasTxHash,
    String executionIntentPublicId,
    String actionStateExecutionIntentPublicId,
    String executionTransactionStatus,
    String receiptStatus,
    String chainOrderState,
    String evidenceErrorCode,
    LocalDateTime evidenceCheckedAt) {

  public static ExecutionTerminationEvidenceView unknown(String executionIntentPublicId) {
    return new ExecutionTerminationEvidenceView(
        null,
        false,
        executionIntentPublicId,
        null,
        null,
        "UNKNOWN",
        "UNKNOWN",
        "EVIDENCE_UNAVAILABLE",
        LocalDateTime.now());
  }
}
