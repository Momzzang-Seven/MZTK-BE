package momzzangseven.mztkbe.global.error.user;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when a user attempts to grant themselves TRAINER privileges illegally. */
public class IllegalTrainerGrantException extends BusinessException {

  /** Create exception with a more specific context message. */
  public IllegalTrainerGrantException(String message) {
    super(
        ErrorCode.ILLEGAL_TRAINER_GRANT,
        ErrorCode.ILLEGAL_TRAINER_GRANT.getMessage() + ": " + message);
  }
}
