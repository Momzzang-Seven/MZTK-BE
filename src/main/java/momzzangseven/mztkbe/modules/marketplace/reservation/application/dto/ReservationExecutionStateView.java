package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Owner-agnostic execution state used only for reservation recovery decisions. */
public record ReservationExecutionStateView(
    String executionIntentId, String status, String actionType, Long requesterUserId) {}
