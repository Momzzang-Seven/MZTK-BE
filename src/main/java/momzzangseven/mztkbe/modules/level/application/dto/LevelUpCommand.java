package momzzangseven.mztkbe.modules.level.application.dto;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.LevelValidationMessage;

public record LevelUpCommand(Long userId) {

  public LevelUpCommand {
    if (userId == null || userId <= 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.USER_ID_POSITIVE);
    }
  }

  public static LevelUpCommand of(Long userId) {
    return new LevelUpCommand(userId);
  }
}
