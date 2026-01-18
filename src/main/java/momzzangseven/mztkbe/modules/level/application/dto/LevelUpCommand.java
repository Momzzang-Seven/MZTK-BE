package momzzangseven.mztkbe.modules.level.application.dto;

public record LevelUpCommand(Long userId) {
  public static LevelUpCommand of(Long userId) {
    return new LevelUpCommand(userId);
  }

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId is required");
    }
  }
}
