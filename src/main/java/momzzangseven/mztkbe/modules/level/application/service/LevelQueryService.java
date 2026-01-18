package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPoliciesResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPolicyItem;
import momzzangseven.mztkbe.modules.level.application.dto.MyLevelResult;
import momzzangseven.mztkbe.modules.level.application.dto.XpPolicyItem;
import momzzangseven.mztkbe.modules.level.application.port.in.GetLevelPoliciesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelPoliciesPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpPoliciesPort;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LevelQueryService implements GetMyLevelUseCase, GetLevelPoliciesUseCase {

  private final LoadUserProgressPort loadUserProgressPort;
  private final LevelPolicyResolver levelPolicyResolver;
  private final LoadLevelPoliciesPort loadLevelPoliciesPort;
  private final LoadXpPoliciesPort loadXpPoliciesPort;

  @Override
  public MyLevelResult execute(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }

    UserProgress progress = loadUserProgressPort.loadOrCreateUserProgress(userId);
    LocalDateTime now = LocalDateTime.now();

    LevelPolicyResolver.NextLevelPolicy nextPolicy =
        levelPolicyResolver.resolveForQuery(progress.getLevel(), now);

    MyLevelResult result =
        MyLevelResult.builder()
            .level(progress.getLevel())
            .availableXp(progress.getAvailableXp())
            .requiredXpForNext(nextPolicy.requiredXpForNext())
            .rewardMztkForNext(nextPolicy.rewardMztkForNext())
            .build();
    result.validate();
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public LevelPoliciesResult execute() {
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

    return LevelPoliciesResult.builder()
        .levelPolicies(levelPolicies)
        .xpPolicies(xpPolicies)
        .build();
  }
}