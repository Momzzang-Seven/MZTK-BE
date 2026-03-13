package momzzangseven.mztkbe.modules.verification.infrastructure.external.level.adapter;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.in.GetTodayWorkoutRewardUseCase;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XpLedgerQueryAdapter implements XpLedgerQueryPort {

  private final GetTodayWorkoutRewardUseCase getTodayWorkoutRewardUseCase;

  @Override
  public TodayRewardSnapshot findTodayWorkoutReward(Long userId, LocalDate earnedOn) {
    var result = getTodayWorkoutRewardUseCase.execute(userId, earnedOn);
    return new TodayRewardSnapshot(
        result.rewarded(), result.grantedXp(), result.earnedDate(), result.sourceRef());
  }
}
