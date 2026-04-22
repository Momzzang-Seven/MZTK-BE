package momzzangseven.mztkbe.modules.marketplace.application.dto;

/** Command for a user to mark a class as completed and trigger settlement. */
public record CompleteReservationCommand(Long reservationId, Long userId) {}
