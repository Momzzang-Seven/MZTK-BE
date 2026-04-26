package momzzangseven.mztkbe.global.error.pagination;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Raised when a cursor pagination request contains invalid cursor or page bounds. */
public class InvalidCursorException extends BusinessException {

  public InvalidCursorException(String message) {
    super(ErrorCode.INVALID_INPUT, message);
  }

  public InvalidCursorException(String message, Throwable cause) {
    super(ErrorCode.INVALID_INPUT, message, cause);
  }
}
