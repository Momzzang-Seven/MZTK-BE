package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;

public record DeleteUserLevelDataCommand(List<Long> userIds) {
  public void validate() {
    if (userIds == null || userIds.isEmpty()) {
      throw new IllegalArgumentException("userIds must not be empty");
    }
  }
}
