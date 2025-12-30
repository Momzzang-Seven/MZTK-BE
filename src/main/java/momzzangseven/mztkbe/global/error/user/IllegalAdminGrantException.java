package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class IllegalAdminGrantException extends BusinessException {

  public IllegalAdminGrantException() {
    super(ErrorCode.ILLEGAL_ADMIN_GRANT, ErrorCode.ILLEGAL_ADMIN_GRANT.getMessage());
  }
}
