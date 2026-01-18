package momzzangseven.mztkbe.modules.level.application.dto;

import lombok.Builder;

@Builder
public record LevelUpCommand(Long userId) {
  public static LevelUpCommand of(Long userId) {
    return LevelUpCommand.builder().userId(userId).build();
  }
}
