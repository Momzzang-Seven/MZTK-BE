package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown after an execution intent has been deliberately persisted in a terminal state. */
public class ExecutionIntentTerminalException extends Web3TransferException {

  public ExecutionIntentTerminalException(ErrorCode errorCode, boolean retryable) {
    super(errorCode, retryable);
  }
}
