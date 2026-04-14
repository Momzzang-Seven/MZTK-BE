package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;

public interface XpLedgerQueryPort {
  TodayRewardSnapshot findTodayWorkoutReward(Long userId, LocalDate earnedOn);
}
