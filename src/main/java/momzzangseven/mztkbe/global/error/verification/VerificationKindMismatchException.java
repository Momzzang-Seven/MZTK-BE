package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class VerificationKindMismatchException extends BusinessException {
  public VerificationKindMismatchException() {
    super(ErrorCode.VERIFICATION_KIND_MISMATCH);
  }
}
