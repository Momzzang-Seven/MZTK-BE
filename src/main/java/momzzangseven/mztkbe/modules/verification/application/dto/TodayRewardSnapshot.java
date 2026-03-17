package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;

public record TodayRewardSnapshot(
    boolean rewarded, int grantedXp, LocalDate earnedDate, String sourceRef) {

  public static TodayRewardSnapshot none(LocalDate date) {
    return new TodayRewardSnapshot(false, 0, date, null);
  }
}
