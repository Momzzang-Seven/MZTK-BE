package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GetLevelPoliciesResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPolicyItem;
import momzzangseven.mztkbe.modules.level.application.dto.XpPolicyItem;
import momzzangseven.mztkbe.modules.level.application.port.in.GetLevelPoliciesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelPoliciesPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpPoliciesPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetLevelPoliciesService implements GetLevelPoliciesUseCase {

  private final LoadLevelPoliciesPort loadLevelPoliciesPort;
  private final LoadXpPoliciesPort loadXpPoliciesPort;

  @Override
  public GetLevelPoliciesResult execute() {
    LocalDateTime now = LocalDateTime.now();

    List<LevelPolicyItem> levelPolicies =
        loadLevelPoliciesPort.loadLevelPolicies(now).stream()
            .map(
                policy ->
                    LevelPolicyItem.builder()
                        .currentLevel(policy.getLevel())
                        .toLevel(policy.getLevel() + 1)
                        .requiredXp(policy.getRequiredXp())
                        .rewardMztk(policy.getRewardMztk())
                        .build())
            .toList();

    List<XpPolicyItem> xpPolicies =
        loadXpPoliciesPort.loadXpPolicies(now).stream()
            .map(
                policy ->
                    XpPolicyItem.builder()
                        .type(policy.getType())
                        .xpAmount(policy.getXpAmount())
                        .dailyCap(policy.getDailyCap())
                        .build())
            .toList();

    return GetLevelPoliciesResult.builder()
        .levelPolicies(levelPolicies)
        .xpPolicies(xpPolicies)
        .build();
  }
}
