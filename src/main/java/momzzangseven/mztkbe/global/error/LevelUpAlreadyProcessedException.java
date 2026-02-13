package momzzangseven.mztkbe.global.error;

public class LevelUpAlreadyProcessedException extends BusinessException {
  public LevelUpAlreadyProcessedException() {
    super(ErrorCode.LEVEL_UP_ALREADY_PROCESSED);
  }
}
