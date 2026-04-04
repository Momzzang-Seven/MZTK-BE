package momzzangseven.mztkbe.modules.user.application.dto;

import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;

/**
 * Output result of {@code GetMyProfileUseCase}. Aggregates user identity, level/XP, attendance, and
 * workout completion data into a single flat structure passed to the API layer.
 */
public record GetMyProfileResult(
    String nickname,
    String email,
    String provider,
    UserRole role,
    String walletAddress,
    int level,
    int currentXp,
    int requiredXpForNextLevel,
    boolean hasAttendedToday,
    boolean hasCompletedWorkoutToday,
    WorkoutCompletedMethod completedWorkoutMethod,
    int weeklyAttendanceCount) {

  /**
   * Assembles a {@code GetMyProfileResult} from the data sources used by {@code
   * GetMyProfileService}.
   *
   * @param user the authenticated user's domain object
   * @param providerName the authentication provider name (e.g. "LOCAL", "KAKAO", "GOOGLE")
   * @param levelInfo level and XP data from the level module
   * @param attendance today's and weekly attendance data from the level module
   * @param workout today's workout completion status from the verification module
   * @param walletAddress the user's active wallet address, or null
   * @return a fully populated result record
   */
  public static GetMyProfileResult from(
      User user,
      String providerName,
      UserLevelInfo levelInfo,
      AttendanceSummary attendance,
      WorkoutCompletionInfo workout,
      String walletAddress) {
    return new GetMyProfileResult(
        user.getNickname(),
        user.getEmail(),
        providerName,
        user.getRole(),
        walletAddress,
        levelInfo.level(),
        levelInfo.currentXp(),
        levelInfo.requiredXpForNextLevel(),
        attendance.hasAttendedToday(),
        workout.hasCompletedWorkoutToday(),
        workout.completedWorkoutMethod(),
        attendance.weeklyAttendanceCount());
  }
}
