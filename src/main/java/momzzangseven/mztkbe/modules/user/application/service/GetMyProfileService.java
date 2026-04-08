package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.user.application.dto.AttendanceSummary;
import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;
import momzzangseven.mztkbe.modules.user.application.dto.UserLevelInfo;
import momzzangseven.mztkbe.modules.user.application.dto.WorkoutCompletionInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.GetMyProfileUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadAttendanceSummaryPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadAuthProviderPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadTodayWorkoutCompletionPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserLevelPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that aggregates a user's profile data from multiple modules. Implements
 * {@link GetMyProfileUseCase} and delegates cross-module data retrieval to output ports so that the
 * application layer never directly depends on infrastructure or other module internals.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyProfileService implements GetMyProfileUseCase {

  private final LoadUserPort loadUserPort;
  private final LoadAuthProviderPort loadAuthProviderPort;
  private final LoadUserLevelPort loadUserLevelPort;
  private final LoadAttendanceSummaryPort loadAttendanceSummaryPort;
  private final LoadTodayWorkoutCompletionPort loadTodayWorkoutCompletionPort;
  private final LoadUserWalletPort loadUserWalletPort;

  /**
   * Loads and assembles the full profile for the given user.
   *
   * @param userId the authenticated user's ID
   * @return a fully populated {@link GetMyProfileResult}
   * @throws UserNotFoundException if no active user exists with the given ID
   */
  @Override
  public GetMyProfileResult execute(Long userId) {
    User user =
        loadUserPort.loadUserById(userId).orElseThrow(() -> new UserNotFoundException(userId));

    String providerName = loadAuthProviderPort.loadProviderName(userId).orElse(null);
    UserLevelInfo levelInfo = loadUserLevelPort.loadLevelInfo(userId);
    AttendanceSummary attendance = loadAttendanceSummaryPort.loadSummary(userId);
    WorkoutCompletionInfo workout = loadTodayWorkoutCompletionPort.loadCompletion(userId);
    String walletAddress = loadUserWalletPort.loadActiveWalletAddress(userId).orElse(null);

    return GetMyProfileResult.from(
        user, providerName, levelInfo, attendance, workout, walletAddress);
  }
}
