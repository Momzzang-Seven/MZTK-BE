package momzzangseven.mztkbe.global.error.level;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class LevelUpCommandInvalidException extends BusinessException {

  public LevelUpCommandInvalidException(String message) {
    super(ErrorCode.LEVEL_UP_COMMAND_INVALID, message);
  }
}
