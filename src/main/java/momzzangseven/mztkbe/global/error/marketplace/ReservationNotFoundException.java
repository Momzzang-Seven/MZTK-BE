package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a reservation is not found. */
public class ReservationNotFoundException extends BusinessException {

  public ReservationNotFoundException(Long reservationId) {
    super(
        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
        "Reservation not found for ID: " + reservationId);
  }

  public ReservationNotFoundException() {
    super(ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND);
  }
}
