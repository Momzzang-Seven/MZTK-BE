package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when reservation date/time does not match the selected slot schedule. */
public class ReservationInvalidSlotDateException extends BusinessException {

  public ReservationInvalidSlotDateException(Long slotId) {
    super(
        ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE,
        "Reservation date/time does not match slot schedule: slotId=" + slotId);
  }
}
