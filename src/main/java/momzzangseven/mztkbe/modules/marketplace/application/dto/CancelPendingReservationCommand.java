package momzzangseven.mztkbe.modules.marketplace.application.dto;

/** Command for the user to cancel their own pending reservation. */
public record CancelPendingReservationCommand(Long reservationId, Long userId) {}
