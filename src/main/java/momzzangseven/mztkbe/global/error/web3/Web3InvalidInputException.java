package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class Web3InvalidInputException extends BusinessException {

  public Web3InvalidInputException(String message) {
    super(ErrorCode.WEB3_INVALID_INPUT, message);
  }
}
