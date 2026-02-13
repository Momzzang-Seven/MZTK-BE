package momzzangseven.mztkbe.global.error;

public class MaxLevelReachedException extends BusinessException {
  public MaxLevelReachedException() {
    super(ErrorCode.MAX_LEVEL_REACHED);
  }

  public MaxLevelReachedException(String customMessage) {
    super(ErrorCode.MAX_LEVEL_REACHED, customMessage);
  }
}
