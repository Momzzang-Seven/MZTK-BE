package momzzangseven.mztkbe.modules.user.application.dto;

/**
 * Carries level and XP information for a user as returned by {@code LoadUserLevelPort}. Used
 * internally by {@code GetMyProfileService} to avoid a direct dependency on the level module.
 */
public record UserLevelInfo(int level, int currentXp, int requiredXpForNextLevel) {}
