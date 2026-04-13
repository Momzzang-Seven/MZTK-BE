package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when an admin account is not found. */
public class AdminAccountNotFoundException extends BusinessException {

  public AdminAccountNotFoundException() {
    super(ErrorCode.ADMIN_NOT_FOUND);
  }
}
