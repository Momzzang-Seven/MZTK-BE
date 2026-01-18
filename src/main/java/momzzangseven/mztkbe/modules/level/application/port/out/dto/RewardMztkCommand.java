package momzzangseven.mztkbe.modules.level.application.port.out.dto;

import lombok.Builder;

/** Command for issuing a level-up reward via {@code RewardMztkPort}. */
@Builder
public record RewardMztkCommand(Long userId, int rewardMztk, Long referenceId) {}

