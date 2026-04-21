package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a marketplace class is not found. */
public class ClassNotFoundException extends BusinessException {

  public ClassNotFoundException(Long classId) {
    super(ErrorCode.MARKETPLACE_CLASS_NOT_FOUND, "Class not found for ID: " + classId);
  }

  public ClassNotFoundException() {
    super(ErrorCode.MARKETPLACE_CLASS_NOT_FOUND);
  }
}
