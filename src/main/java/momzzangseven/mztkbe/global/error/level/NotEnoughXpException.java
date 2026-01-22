package momzzangseven.mztkbe.global.error.level;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class NotEnoughXpException extends BusinessException {
  public NotEnoughXpException() {
    super(ErrorCode.NOT_ENOUGH_XP);
  }

  public NotEnoughXpException(String customMessage) {
    super(ErrorCode.NOT_ENOUGH_XP, customMessage);
  }
}
