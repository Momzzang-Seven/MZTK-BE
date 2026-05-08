package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;

/**
 * Input port for fetching a user's own reservation list (cursor-paginated).
 *
 * <p>Returns a {@link CursorSlice} containing the page items, a {@code hasNext} flag, and a {@code
 * nextCursor} token that the client can pass on the next request.
 */
public interface GetUserReservationsUseCase {

  /**
   * Returns one page of the user's reservations, optionally filtered by status.
   *
   * @param query contains the authenticated user's ID, optional status filter, and cursor params
   * @return slice with items, hasNext flag, and nextCursor token
   */
  CursorSlice<ReservationSummaryResult> execute(GetUserReservationsQuery query);
}
