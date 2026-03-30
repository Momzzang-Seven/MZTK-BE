package momzzangseven.mztkbe.modules.user.application.port.out;

import momzzangseven.mztkbe.modules.user.application.dto.WorkoutCompletionInfo;

/**
 * Output port for checking whether a user has completed their workout today. Implemented by an
 * infrastructure adapter that delegates to the verification module.
 */
public interface LoadTodayWorkoutCompletionPort {

  /**
   * Loads today's workout completion status for the given user.
   *
   * @param userId the user's ID
   * @return the user's workout completion info for today
   */
  WorkoutCompletionInfo loadCompletion(Long userId);
}
