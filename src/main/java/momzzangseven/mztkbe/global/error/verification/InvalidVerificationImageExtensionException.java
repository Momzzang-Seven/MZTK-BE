package momzzangseven.mztkbe.global.error.verification;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidVerificationImageExtensionException extends BusinessException {
  public InvalidVerificationImageExtensionException() {
    super(ErrorCode.VERIFICATION_INVALID_IMAGE_EXTENSION);
  }
}
