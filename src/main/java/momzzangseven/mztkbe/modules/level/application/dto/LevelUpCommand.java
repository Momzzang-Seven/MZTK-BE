package momzzangseven.mztkbe.modules.level.application.dto;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;

public record LevelUpCommand(Long userId) {

  public LevelUpCommand {
    if (userId == null || userId <= 0) {
      throw new LevelUpCommandInvalidException("userId is required");
    }
  }

  public static LevelUpCommand of(Long userId) {
    return new LevelUpCommand(userId);
  }
}
