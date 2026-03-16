package momzzangseven.mztkbe.modules.verification.application.port.in;

import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;

public interface GetTodayWorkoutCompletionUseCase {
  TodayWorkoutCompletionResult execute(Long userId);
}
