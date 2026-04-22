package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class slot time overlaps with an existing slot. */
public class SlotTimeConflictException extends BusinessException {

  public SlotTimeConflictException() {
    super(ErrorCode.MARKETPLACE_SLOT_TIME_CONFLICT);
  }

  public SlotTimeConflictException(String message) {
    super(ErrorCode.MARKETPLACE_SLOT_TIME_CONFLICT, message);
  }
}
