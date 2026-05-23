package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;

public record ReservationEscrowExecutionTerminatedCommand(
    String executionIntentPublicId,
    String actionType,
    String actorType,
    Long reservationId,
    String pendingAttemptToken,
    Long actionStateId,
    String terminalStatus,
    String failureReason,
    String reasonCode,
    ReservationEscrowExecutionTerminationEvidence evidence) {

  public ReservationEscrowExecutionTerminatedCommand(
      String executionIntentPublicId,
      String actionType,
      String actorType,
      Long reservationId,
      String pendingAttemptToken,
      Long actionStateId,
      String terminalStatus,
      String failureReason) {
    this(
        executionIntentPublicId,
        actionType,
        actorType,
        reservationId,
        pendingAttemptToken,
        actionStateId,
        terminalStatus,
        failureReason,
        null,
        ReservationEscrowExecutionTerminationEvidence.unknown(executionIntentPublicId));
  }

  public record ReservationEscrowExecutionTerminationEvidence(
      String txHash,
      boolean hasTxHash,
      String executionIntentPublicId,
      String actionStateExecutionIntentPublicId,
      String executionTransactionStatus,
      String receiptStatus,
      String chainOrderState,
      String evidenceErrorCode,
      LocalDateTime evidenceCheckedAt) {

    public static ReservationEscrowExecutionTerminationEvidence unknown(
        String executionIntentPublicId) {
      return new ReservationEscrowExecutionTerminationEvidence(
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
}
