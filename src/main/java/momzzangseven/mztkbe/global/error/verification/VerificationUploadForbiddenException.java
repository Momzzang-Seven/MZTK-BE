package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class VerificationUploadForbiddenException extends BusinessException {
  public VerificationUploadForbiddenException() {
    super(ErrorCode.VERIFICATION_UPLOAD_FORBIDDEN);
  }
}
