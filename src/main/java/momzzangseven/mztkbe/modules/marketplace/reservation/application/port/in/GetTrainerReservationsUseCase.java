package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;

/**
 * Input port for fetching incoming reservations assigned to a trainer.
 *
 * <p>Returns reservations ordered by {@code reservationDate} descending so upcoming or recent
 * sessions appear first.
 */
public interface GetTrainerReservationsUseCase {

  /**
   * Returns a list of reservations targeting the authenticated trainer, optionally filtered by
   * status.
   *
   * @param query contains the authenticated trainer's ID and an optional status filter
   * @return list of reservation summaries; empty list if none found
   */
  List<ReservationSummaryResult> execute(GetTrainerReservationsQuery query);
}
