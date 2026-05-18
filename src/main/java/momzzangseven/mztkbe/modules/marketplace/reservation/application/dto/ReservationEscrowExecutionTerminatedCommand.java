package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

public record ReservationEscrowExecutionTerminatedCommand(
    String executionIntentPublicId,
    String actionType,
    String actorType,
    Long reservationId,
    String pendingAttemptToken,
    Long actionStateId,
    String terminalStatus,
    String failureReason) {}
