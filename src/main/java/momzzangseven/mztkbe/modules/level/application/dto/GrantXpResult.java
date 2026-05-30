package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;

/**
 * Standard result model for XP grants.
 *
 * <p>External domains should use {@link #status()} + {@link #grantedXp()} to determine whether XP
 * was actually granted and how much was applied.
 */
public record GrantXpResult(
    Status status,
    int grantedXp,
    int dailyCap,
    int grantedCountToday,
    int remainingCountToday,
    LocalDate earnedOn) {

  public enum Status {
    GRANTED,
    ALREADY_GRANTED,
    DAILY_CAP_REACHED,
    /** Synchronous grant failed and was enqueued to the outbox for guaranteed retry. */
    DEFERRED
  }

  /**
   * The synchronous grant failed and was handed to the outbox; no XP is reflected in the immediate
   * response, but a reconciler will retry the (idempotent) grant.
   */
  public static GrantXpResult deferred(LocalDate earnedOn) {
    return new GrantXpResult(Status.DEFERRED, 0, 0, 0, 0, earnedOn);
  }

  public static GrantXpResult granted(
      int grantedXp, int dailyCap, int grantedCountToday, LocalDate earnedOn) {
    return new GrantXpResult(
        Status.GRANTED,
        grantedXp,
        dailyCap,
        grantedCountToday,
        remainingCountToday(dailyCap, grantedCountToday),
        earnedOn);
  }

  public static GrantXpResult alreadyGranted(
      int dailyCap, int grantedCountToday, LocalDate earnedOn) {
    return new GrantXpResult(
        Status.ALREADY_GRANTED,
        0,
        dailyCap,
        grantedCountToday,
        remainingCountToday(dailyCap, grantedCountToday),
        earnedOn);
  }

  public static GrantXpResult dailyCapReached(
      int dailyCap, int grantedCountToday, LocalDate earnedOn) {
    return new GrantXpResult(
        Status.DAILY_CAP_REACHED,
        0,
        dailyCap,
        grantedCountToday,
        remainingCountToday(dailyCap, grantedCountToday),
        earnedOn);
  }

  private static int remainingCountToday(int dailyCap, int grantedCountToday) {
    if (dailyCap < 0) {
      return -1;
    }
    return Math.max(0, dailyCap - grantedCountToday);
  }
}
