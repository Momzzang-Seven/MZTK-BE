package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a user attempts to complete a reservation before the class starts. */
public class ReservationEarlyCompleteException extends BusinessException {

  public ReservationEarlyCompleteException(Long reservationId) {
    super(
        ErrorCode.MARKETPLACE_RESERVATION_EARLY_COMPLETE,
        "Cannot complete reservation before the class starts: reservationId=" + reservationId);
  }

  /** Used when reservation ID is already available in context. */
  public ReservationEarlyCompleteException() {
    super(ErrorCode.MARKETPLACE_RESERVATION_EARLY_COMPLETE);
  }
}
