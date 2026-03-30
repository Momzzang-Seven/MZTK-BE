package momzzangseven.mztkbe.modules.user.infrastructure.external.level.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUseCase;
import momzzangseven.mztkbe.modules.user.application.dto.UserLevelInfo;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserLevelPort;
import org.springframework.stereotype.Component;

/**
 * Driven adapter that bridges the user module's {@link LoadUserLevelPort} to the level module's
 * {@link GetMyLevelUseCase}. Converts {@link GetMyLevelResult} into the user-module-scoped {@link
 * UserLevelInfo} DTO to maintain strict module boundaries.
 */
@Component
@RequiredArgsConstructor
public class UserLevelAdapter implements LoadUserLevelPort {

  private final GetMyLevelUseCase getMyLevelUseCase;

  /**
   * Fetches level and XP data for the given user by delegating to the level module.
   *
   * @param userId the user's ID
   * @return the user's current level and XP snapshot
   */
  @Override
  public UserLevelInfo loadLevelInfo(Long userId) {
    GetMyLevelResult result = getMyLevelUseCase.execute(userId);
    return new UserLevelInfo(result.level(), result.availableXp(), result.requiredXpForNext());
  }
}
