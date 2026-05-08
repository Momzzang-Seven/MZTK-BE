package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Query for fetching a user's own reservation list (cursor-paginated).
 *
 * @param userId the authenticated user's ID
 * @param status optional status filter; null means all statuses
 * @param pageRequest cursor and page-size parameters
 */
public record GetUserReservationsQuery(
    Long userId, ReservationStatus status, CursorPageRequest pageRequest) {

  /** Cursor scope identifier shared across Controller, Query, and Service layers. */
  public static final String CURSOR_SCOPE = "user-reservations";

  /** Convenience constructor for tests / callers that do not supply a cursor yet. */
  public GetUserReservationsQuery(Long userId, ReservationStatus status) {
    this(userId, status, CursorPageRequest.of(null, null, 20, 100, CURSOR_SCOPE));
  }

  public void validate() {
    if (userId == null || userId <= 0)
      throw new IllegalArgumentException("userId must be a positive number");
  }
}
