package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class Web3ConfigInvalidException extends BusinessException {

  public Web3ConfigInvalidException(String message) {
    super(ErrorCode.WEB3_CONFIG_INVALID, message);
  }

  public Web3ConfigInvalidException(String message, Throwable cause) {
    super(ErrorCode.WEB3_CONFIG_INVALID, message, cause);
  }
}
