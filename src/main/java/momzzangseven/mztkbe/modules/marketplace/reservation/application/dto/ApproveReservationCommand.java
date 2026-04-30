package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Command for a trainer to approve a pending reservation. */
public record ApproveReservationCommand(Long reservationId, Long authenticatedTrainerId) {}
