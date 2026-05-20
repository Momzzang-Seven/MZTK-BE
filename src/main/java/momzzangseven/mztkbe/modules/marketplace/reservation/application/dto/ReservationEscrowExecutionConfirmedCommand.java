package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;

public record ReservationEscrowExecutionConfirmedCommand(
    String executionIntentPublicId,
    String txHash,
    String actionType,
    String actorType,
    Long reservationId,
    String orderKey,
    Long expectedContractDeadlineEpochSeconds,
    Long contractDeadlineEpochSeconds,
    LocalDateTime sessionEndAt,
    String pendingAttemptToken,
    Long actionStateId) {}
