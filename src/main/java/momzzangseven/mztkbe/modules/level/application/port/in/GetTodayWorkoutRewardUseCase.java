package momzzangseven.mztkbe.modules.level.application.port.in;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.application.dto.GetTodayWorkoutRewardResult;

public interface GetTodayWorkoutRewardUseCase {
  GetTodayWorkoutRewardResult execute(Long userId, LocalDate earnedOn);
}
