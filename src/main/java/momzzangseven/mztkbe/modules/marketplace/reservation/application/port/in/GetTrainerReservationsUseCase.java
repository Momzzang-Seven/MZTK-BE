package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;

/**
 * Input port for fetching incoming reservations assigned to a trainer (cursor-paginated).
 *
 * <p>Returns a {@link CursorSlice} containing the page items, a {@code hasNext} flag, and a {@code
 * nextCursor} token that the client can pass on the next request.
 */
public interface GetTrainerReservationsUseCase {

  /**
   * Returns one page of reservations targeting the authenticated trainer, optionally filtered by
   * status.
   *
   * @param query contains the authenticated trainer's ID, optional status filter, and cursor params
   * @return slice with items, hasNext flag, and nextCursor token
   */
  CursorSlice<ReservationSummaryResult> execute(GetTrainerReservationsQuery query);
}
