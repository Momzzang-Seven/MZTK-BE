package momzzangseven.mztkbe.global.error;

public class LevelUpCommandInvalidException extends BusinessException {

  public LevelUpCommandInvalidException(String message) {
    super(ErrorCode.LEVEL_UP_COMMAND_INVALID, message);
  }
}
