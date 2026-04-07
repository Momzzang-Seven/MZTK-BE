package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when reactivation is attempted on an account that is not soft-deleted. */
public class AccountNotDeletedException extends BusinessException {

  public AccountNotDeletedException() {
    super(ErrorCode.ACCOUNT_NOT_DELETED);
  }
}
