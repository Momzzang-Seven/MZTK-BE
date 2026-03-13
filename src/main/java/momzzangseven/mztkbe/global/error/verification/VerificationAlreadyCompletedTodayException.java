package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class VerificationAlreadyCompletedTodayException extends BusinessException {
  public VerificationAlreadyCompletedTodayException() {
    super(ErrorCode.VERIFICATION_ALREADY_COMPLETED_TODAY);
  }
}
