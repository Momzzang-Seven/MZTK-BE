package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class VerificationNotFoundException extends BusinessException {
  public VerificationNotFoundException() {
    super(ErrorCode.VERIFICATION_NOT_FOUND);
  }
}
