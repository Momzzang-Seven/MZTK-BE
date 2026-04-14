package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidTmpObjectKeyException extends BusinessException {
  public InvalidTmpObjectKeyException() {
    super(ErrorCode.VERIFICATION_INVALID_TMP_OBJECT_KEY);
  }
}
