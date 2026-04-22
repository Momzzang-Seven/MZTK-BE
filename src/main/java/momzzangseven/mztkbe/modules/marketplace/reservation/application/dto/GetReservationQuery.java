package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/**
 * Query for fetching a single reservation's detail.
 *
 * @param reservationId the reservation's primary key
 * @param requesterId the authenticated user or trainer's ID (used for ownership check)
 */
public record GetReservationQuery(Long reservationId, Long requesterId) {}
