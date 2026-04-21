package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when trying to deactivate/delete a slot that has active reservations. */
public class SlotHasActiveReservationException extends BusinessException {

  public SlotHasActiveReservationException(Long slotId) {
    super(
        ErrorCode.MARKETPLACE_SLOT_HAS_ACTIVE_RESERVATION,
        "Slot " + slotId + " has active reservations and cannot be deactivated");
  }

  public SlotHasActiveReservationException() {
    super(ErrorCode.MARKETPLACE_SLOT_HAS_ACTIVE_RESERVATION);
  }
}
