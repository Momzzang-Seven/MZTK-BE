package momzzangseven.mztkbe.global.error;

import lombok.Getter;

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
}
