package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Command for a trainer to reject a pending reservation. */
public record RejectReservationCommand(
    Long reservationId, Long authenticatedTrainerId, String rejectionReason) {}
