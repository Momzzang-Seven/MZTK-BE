package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;

public record GetTodayWorkoutRewardResult(
    boolean rewarded, int grantedXp, LocalDate earnedDate, String sourceRef) {

  public static GetTodayWorkoutRewardResult none(LocalDate earnedDate) {
    return new GetTodayWorkoutRewardResult(false, 0, earnedDate, null);
  }

  public static GetTodayWorkoutRewardResult from(XpLedgerEntry entry) {
    return new GetTodayWorkoutRewardResult(
        true, entry.getXpAmount(), entry.getEarnedOn(), entry.getSourceRef());
  }
}
