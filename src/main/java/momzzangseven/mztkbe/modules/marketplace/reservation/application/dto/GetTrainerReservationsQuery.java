package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Query for fetching a trainer's incoming reservation list (cursor-paginated).
 *
 * @param trainerId the authenticated trainer's ID
 * @param status optional status filter; null means all statuses
 * @param pageRequest cursor and page-size parameters
 */
public record GetTrainerReservationsQuery(
    Long trainerId, ReservationStatus status, CursorPageRequest pageRequest) {

  /** Convenience constructor for tests / callers that do not supply a cursor yet. */
  public GetTrainerReservationsQuery(Long trainerId, ReservationStatus status) {
    this(trainerId, status, CursorPageRequest.of(null, null, 20, 100, "trainer-reservations"));
  }

  public void validate() {
    if (trainerId == null || trainerId <= 0)
      throw new IllegalArgumentException("trainerId must be a positive number");
  }
}
