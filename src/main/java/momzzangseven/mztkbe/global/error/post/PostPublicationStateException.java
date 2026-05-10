package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Publication-state conflict for question post mutation/recovery flows. */
public class PostPublicationStateException extends BusinessException {

  public PostPublicationStateException(ErrorCode errorCode) {
    super(errorCode);
  }

  public PostPublicationStateException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
