package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Command for a buyer to claim a deadline-expired escrow refund. */
public record ClaimExpiredRefundReservationCommand(Long reservationId, Long userId) {}
