package momzzangseven.mztkbe.global.error;

public class Web3TransactionStateInvalidException extends BusinessException {

  public Web3TransactionStateInvalidException(String message) {
    super(ErrorCode.WEB3_TRANSACTION_STATE_INVALID, message);
  }

  public Web3TransactionStateInvalidException(String message, Throwable cause) {
    super(ErrorCode.WEB3_TRANSACTION_STATE_INVALID, message, cause);
  }
}
