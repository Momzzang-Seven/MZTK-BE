package momzzangseven.mztkbe.global.error.level;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class LevelUpAlreadyProcessedException extends BusinessException {
  public LevelUpAlreadyProcessedException() {
    super(ErrorCode.LEVEL_UP_ALREADY_PROCESSED);
  }
}

