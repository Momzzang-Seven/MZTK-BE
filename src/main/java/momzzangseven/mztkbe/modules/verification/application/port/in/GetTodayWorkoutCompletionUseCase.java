package momzzangseven.mztkbe.modules.verification.application.port.in;

import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;

/** Query use case for the "today workout completion" read model. */
public interface GetTodayWorkoutCompletionUseCase {
  TodayWorkoutCompletionResult execute(Long userId);
}
