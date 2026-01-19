package momzzangseven.mztkbe.global.error.level;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class MaxLevelReachedException extends BusinessException {
  public MaxLevelReachedException() {
    super(ErrorCode.MAX_LEVEL_REACHED);
  }

  public MaxLevelReachedException(String customMessage) {
    super(ErrorCode.MAX_LEVEL_REACHED, customMessage);
  }
}
