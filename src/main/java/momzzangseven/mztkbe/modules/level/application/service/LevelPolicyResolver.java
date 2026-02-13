package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.level.MaxLevelReachedException;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelPolicyResolver {

  private final PolicyPort policyPort;

  public NextLevelPolicyInfo resolveNextLevelInfo(int currentLevel, LocalDateTime at) {
    if (at == null) {
      throw new IllegalArgumentException("at must not be null");
    }

    return policyPort
        .loadLevelPolicy(currentLevel, at)
        .map(policy -> new NextLevelPolicyInfo(policy.getRequiredXp(), policy.getRewardMztk()))
        .orElseGet(
            () -> {
              if (isMaxLevel(currentLevel, at)) {
                return new NextLevelPolicyInfo(0, 0);
              }
              throw new IllegalStateException("Level policy not found: level=" + currentLevel);
            });
  }

  public LevelPolicy resolveLevelUpPolicy(int currentLevel, LocalDateTime at) {
    if (at == null) {
      throw new IllegalArgumentException("at must not be null");
    }

    return policyPort
        .loadLevelPolicy(currentLevel, at)
        .orElseThrow(
            () -> {
              if (isMaxLevel(currentLevel, at)) {
                return new MaxLevelReachedException("Max level reached: level=" + currentLevel);
              }
              return new IllegalStateException("Level policy not found: level=" + currentLevel);
            });
  }

  private boolean isMaxLevel(int currentLevel, LocalDateTime at) {
    List<LevelPolicy> policies = policyPort.loadLevelPolicies(at);
    if (policies.isEmpty()) {
      log.error("No level policies configured");
      throw new IllegalStateException("No level policies configured");
    }

    int maxPolicyLevel = policies.get(policies.size() - 1).getLevel();
    int maxSupportedLevel = maxPolicyLevel + 1;
    return currentLevel >= maxSupportedLevel;
  }

  public record NextLevelPolicyInfo(int requiredXpForNext, int rewardMztkForNext) {}
}
