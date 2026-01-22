package momzzangseven.mztkbe.modules.level.application.dto;

import lombok.Builder;

/** A single fixed level policy row (1~29 seed). */
@Builder
public record LevelPolicyItem(int currentLevel, int toLevel, int requiredXp, int rewardMztk) {}
