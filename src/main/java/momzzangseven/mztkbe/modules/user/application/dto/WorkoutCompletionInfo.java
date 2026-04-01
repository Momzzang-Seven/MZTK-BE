package momzzangseven.mztkbe.modules.user.application.dto;

import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;

/**
 * Carries today's workout completion status and the method used for verification, as returned by
 * {@code LoadTodayWorkoutCompletionPort}. Used internally by {@code GetMyProfileService}.
 */
public record WorkoutCompletionInfo(
    boolean hasCompletedWorkoutToday, WorkoutCompletedMethod completedWorkoutMethod) {}
