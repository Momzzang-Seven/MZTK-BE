package momzzangseven.mztkbe.global.error.web3;

import lombok.Getter;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Policy exception for EIP-7702 transfer flow including retryable metadata. */
@Getter
public class Web3TransferException extends BusinessException {

  private final boolean retryable;

  public Web3TransferException(ErrorCode errorCode, String message, boolean retryable) {
    super(errorCode, message);
    this.retryable = retryable;
  }

  public Web3TransferException(ErrorCode errorCode, boolean retryable) {
    super(errorCode);
    this.retryable = retryable;
  }

  public Web3TransferException(
      ErrorCode errorCode, String message, Throwable cause, boolean retryable) {
    super(errorCode, message, cause);
    this.retryable = retryable;
  }
}
