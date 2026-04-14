package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when admin credential generation fails after retries. */
public class AdminCredentialGenerationException extends BusinessException {

  public AdminCredentialGenerationException(String message) {
    super(ErrorCode.ADMIN_CREDENTIAL_GEN_FAILED, message);
  }
}
