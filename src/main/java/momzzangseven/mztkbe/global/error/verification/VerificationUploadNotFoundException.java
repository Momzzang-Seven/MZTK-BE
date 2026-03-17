package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class VerificationUploadNotFoundException extends BusinessException {
  public VerificationUploadNotFoundException() {
    super(ErrorCode.VERIFICATION_UPLOAD_NOT_FOUND);
  }
}
