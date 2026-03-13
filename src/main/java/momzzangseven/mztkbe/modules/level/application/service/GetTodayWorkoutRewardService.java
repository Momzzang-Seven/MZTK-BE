package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GetTodayWorkoutRewardResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetTodayWorkoutRewardUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTodayWorkoutRewardService implements GetTodayWorkoutRewardUseCase {

  private final XpLedgerPort xpLedgerPort;

  @Override
  public GetTodayWorkoutRewardResult execute(Long userId, LocalDate earnedOn) {
    return xpLedgerPort
        .findLatestByUserIdAndTypeAndEarnedOn(userId, XpType.WORKOUT, earnedOn)
        .map(GetTodayWorkoutRewardResult::from)
        .orElse(GetTodayWorkoutRewardResult.none(earnedOn));
  }
}
