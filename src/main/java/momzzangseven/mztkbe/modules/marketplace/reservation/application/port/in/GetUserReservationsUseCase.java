package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;

/**
 * Input port for fetching a user's own reservation list.
 *
 * <p>Returns reservations ordered by {@code reservationDate} descending so the most recent session
 * appears first.
 */
public interface GetUserReservationsUseCase {

  /**
   * Returns a pageable-free list of the user's reservations, optionally filtered by status.
   *
   * @param query contains the authenticated user's ID and an optional status filter
   * @return list of reservation summaries; empty list if none found
   */
  List<ReservationSummaryResult> execute(GetUserReservationsQuery query);
}
