package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.LevelValidationMessage;

public record DeleteUserLevelDataCommand(List<Long> userIds) {

  public DeleteUserLevelDataCommand {
    if (userIds == null || userIds.isEmpty()) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.USER_IDS_NOT_EMPTY);
    }
  }
}
