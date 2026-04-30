package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;

/**
 * Input port for fetching a single reservation's detail.
 *
 * <p>Both the owning user and the associated trainer are permitted to view the reservation detail.
 */
public interface GetReservationDetailUseCase {

  /**
   * Returns the full detail of a reservation.
   *
   * @param query contains the reservation ID and the requester's ID for ownership verification
   * @return reservation detail result
   * @throws momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException if not found
   * @throws momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException if
   *     the requester is neither the owning user nor the associated trainer
   */
  GetReservationResult execute(GetReservationQuery query);
}
