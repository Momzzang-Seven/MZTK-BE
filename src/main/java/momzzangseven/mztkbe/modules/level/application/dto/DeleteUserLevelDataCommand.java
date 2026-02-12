package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;

public record DeleteUserLevelDataCommand(List<Long> userIds) {

  public DeleteUserLevelDataCommand {
    if (userIds == null || userIds.isEmpty()) {
      throw new LevelUpCommandInvalidException("userIds must not be empty");
    }
  }
}
