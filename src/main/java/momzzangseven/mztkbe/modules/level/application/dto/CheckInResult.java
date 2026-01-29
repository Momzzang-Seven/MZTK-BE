package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;

public record CheckInResult(
    boolean success,
    LocalDate attendedDate,
    int grantedXp,
    int bonusXp,
    int streakDays,
    String message) {
  public static CheckInResult alreadyCheckedIn(LocalDate date) {
    return new CheckInResult(false, date, 0, 0, 0, "ALREADY_CHECKED_IN");
  }

  public static CheckInResult success(LocalDate date, int grantedXp, int bonusXp, int streakDays) {
    return new CheckInResult(true, date, grantedXp, bonusXp, streakDays, "OK");
  }
}
