package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/**
 * Query for fetching a single reservation's detail.
 *
 * @param reservationId the reservation's primary key
 * @param requesterId the authenticated user or trainer's ID (used for ownership check)
 */
public record GetReservationQuery(Long reservationId, Long requesterId) {

  public void validate() {
    if (reservationId == null || reservationId <= 0)
      throw new IllegalArgumentException("reservationId must be a positive number");
    if (requesterId == null || requesterId <= 0)
      throw new IllegalArgumentException("requesterId must be a positive number");
  }
}
