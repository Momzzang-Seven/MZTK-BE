package momzzangseven.mztkbe.modules.user.api.dto;

import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;

/**
 * API response DTO for {@code GET /users/me}. A flat projection of the authenticated user's
 * identity, level/XP, attendance, and today's workout completion status.
 */
public record GetMyProfileResponseDTO(
    String nickname,
    String email,
    String provider,
    String walletAddress,
    int level,
    int currentXp,
    int requiredXpForNextLevel,
    boolean hasAttendedToday,
    boolean hasCompletedWorkoutToday,
    WorkoutCompletedMethod completedWorkoutMethod,
    int weeklyAttendanceCount) {

  /**
   * Creates a {@code GetMyProfileResponseDTO} from the application-layer result.
   *
   * @param result the aggregated profile result from {@code GetMyProfileUseCase}
   * @return the populated response DTO
   */
  public static GetMyProfileResponseDTO from(GetMyProfileResult result) {
    return new GetMyProfileResponseDTO(
        result.nickname(),
        result.email(),
        result.provider(),
        result.walletAddress(),
        result.level(),
        result.currentXp(),
        result.requiredXpForNextLevel(),
        result.hasAttendedToday(),
        result.hasCompletedWorkoutToday(),
        result.completedWorkoutMethod(),
        result.weeklyAttendanceCount());
  }
}
