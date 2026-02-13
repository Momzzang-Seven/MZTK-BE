package momzzangseven.mztkbe.global.error;

public class Web3InvalidInputException extends BusinessException {

  public Web3InvalidInputException(String message) {
    super(ErrorCode.WEB3_INVALID_INPUT, message);
  }
}
