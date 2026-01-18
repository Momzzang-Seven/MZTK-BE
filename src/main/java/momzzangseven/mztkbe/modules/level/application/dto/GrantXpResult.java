package momzzangseven.mztkbe.modules.level.application.dto;

public record GrantXpResult(GrantXpStatus status, int grantedXp, int dailyCap) {

  public static GrantXpResult granted(int grantedXp, int dailyCap) {
    return new GrantXpResult(GrantXpStatus.GRANTED, grantedXp, dailyCap);
  }

  public static GrantXpResult alreadyGranted(int dailyCap) {
    return new GrantXpResult(GrantXpStatus.ALREADY_GRANTED, 0, dailyCap);
  }

  public static GrantXpResult dailyCapReached(int dailyCap) {
    return new GrantXpResult(GrantXpStatus.DAILY_CAP_REACHED, 0, dailyCap);
  }
}
