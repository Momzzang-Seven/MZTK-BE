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
    Long userId, ReservationListStatusFilter status, CursorPageRequest pageRequest) {

  /**
   * Base cursor scope prefix. The effective scope is status-aware: {@link
   * #cursorScope(ReservationListStatusFilter)} must be used to construct a fully-qualified scope
   * string so that a cursor issued for one status filter cannot be replayed against a different
   * filter.
   */
  public static final String CURSOR_SCOPE_PREFIX = "user-reservations";

  /**
   * Returns the cursor scope string that encodes the given status filter.
   *
   * <ul>
   *   <li>{@code null} status (all reservations) → {@code "user-reservations:ALL"}
   *   <li>specific status → {@code "user-reservations:APPROVED"} etc.
   * </ul>
   *
   * <p>Both the controller (when building {@link CursorPageRequest}) and the service (when encoding
   * the next-cursor token) must call this method with the same {@code status} value so that the
   * scope embedded in the token matches the scope expected at decode time.
   */
  public static String cursorScope(ReservationListStatusFilter status) {
    return CURSOR_SCOPE_PREFIX + ":" + (status == null ? "ALL" : status.name());
  }

  /** Transitional constructor for existing internal tests/callers that still pass stored status. */
  public GetUserReservationsQuery(
      Long userId, ReservationStatus status, CursorPageRequest pageRequest) {
    this(userId, fromStoredStatus(status), pageRequest);
  }

  /** Transitional constructor for existing internal tests/callers that still pass stored status. */
  public GetUserReservationsQuery(Long userId, ReservationStatus status) {
    this(
        userId,
        fromStoredStatus(status),
        CursorPageRequest.of(null, null, 20, 100, cursorScope(fromStoredStatus(status))));
  }

  /** Transitional cursor-scope helper for existing internal tests/callers. */
  public static String cursorScope(ReservationStatus status) {
    return cursorScope(fromStoredStatus(status));
  }

  private static ReservationListStatusFilter fromStoredStatus(ReservationStatus status) {
    return status == null ? null : ReservationListStatusFilter.valueOf(status.name());
  }

  public void validate() {
    if (userId == null || userId <= 0)
      throw new IllegalArgumentException("userId must be a positive number");
  }
}
