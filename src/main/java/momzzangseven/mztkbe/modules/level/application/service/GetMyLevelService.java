package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.MyLevelResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadUserProgressPort;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyLevelService implements GetMyLevelUseCase {

  private final LoadUserProgressPort loadUserProgressPort;
  private final LevelPolicyResolver levelPolicyResolver;

  @Override
  public MyLevelResult execute(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }

    UserProgress progress = loadUserProgressPort.loadOrCreateUserProgress(userId);
    LocalDateTime now = LocalDateTime.now();

    LevelPolicyResolver.NextLevelPolicyInfo nextPolicy =
        levelPolicyResolver.resolveNextLevelInfo(progress.getLevel(), now);

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
}
