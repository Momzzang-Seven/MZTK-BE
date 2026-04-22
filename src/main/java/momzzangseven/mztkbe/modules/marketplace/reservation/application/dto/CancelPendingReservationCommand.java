package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Command for the user to cancel their own pending reservation. */
public record CancelPendingReservationCommand(Long reservationId, Long userId) {}
