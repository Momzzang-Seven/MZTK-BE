package momzzangseven.mztkbe.modules.user.infrastructure.external.verification.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.dto.WorkoutCompletionInfo;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadTodayWorkoutCompletionPort;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetTodayWorkoutCompletionUseCase;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import org.springframework.stereotype.Component;

/**
 * Driven adapter that bridges the user module's {@link LoadTodayWorkoutCompletionPort} to the
 * verification module's {@link GetTodayWorkoutCompletionUseCase}. Converts {@link
 * TodayWorkoutCompletionResult} into the user-module-scoped {@link WorkoutCompletionInfo} DTO,
 * translating {@link CompletedMethod} from the verification module into the user-module-scoped
 * {@link WorkoutCompletedMethod}.
 */
@Component
@RequiredArgsConstructor
public class TodayWorkoutCompletionAdapter implements LoadTodayWorkoutCompletionPort {

  private final GetTodayWorkoutCompletionUseCase getTodayWorkoutCompletionUseCase;

  /**
   * Checks whether the given user has completed their workout today.
   *
   * @param userId the user's ID
   * @return workout completion info for today
   */
  @Override
  public WorkoutCompletionInfo loadCompletion(Long userId) {
    TodayWorkoutCompletionResult result = getTodayWorkoutCompletionUseCase.execute(userId);
    return new WorkoutCompletionInfo(
        result.todayCompleted(), toUserMethod(result.completedMethod()));
  }

  private WorkoutCompletedMethod toUserMethod(CompletedMethod method) {
    if (method == null) {
      return WorkoutCompletedMethod.UNKNOWN;
    }
    return switch (method) {
      case LOCATION -> WorkoutCompletedMethod.LOCATION;
      case WORKOUT_PHOTO -> WorkoutCompletedMethod.WORKOUT_PHOTO;
      case WORKOUT_RECORD -> WorkoutCompletedMethod.WORKOUT_RECORD;
      case UNKNOWN -> WorkoutCompletedMethod.UNKNOWN;
    };
  }
}
