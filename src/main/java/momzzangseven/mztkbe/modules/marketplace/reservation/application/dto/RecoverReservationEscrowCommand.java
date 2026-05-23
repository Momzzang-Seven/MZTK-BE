package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Command for buyer/trainer initiated marketplace execution recovery. */
public record RecoverReservationEscrowCommand(Long reservationId, Long requesterId) {}
