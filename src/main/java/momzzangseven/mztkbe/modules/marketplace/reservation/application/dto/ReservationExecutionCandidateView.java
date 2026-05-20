package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Owner-agnostic execution candidate used by reservation recovery and expired-hold guards. */
public record ReservationExecutionCandidateView(
    String executionIntentId,
    String status,
    String actionType,
    Long requesterUserId,
    Long transactionId,
    String transactionStatus,
    String txHash,
    PayloadEvidence payloadEvidence,
    boolean payloadEvidenceValid) {

  public record PayloadEvidence(
      Integer payloadVersion,
      Long reservationId,
      Long escrowId,
      Long actionStateId,
      String pendingAttemptToken,
      String orderKey,
      String actionType) {}
}
