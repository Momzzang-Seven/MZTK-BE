package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Result for one marketplace admin preparation recovery batch. */
public record RecoverExpiredMarketplaceAdminExecutionAttemptResult(
    int scanned, int recovered, int skipped, int failed) {

  public boolean isEmpty() {
    return scanned == 0 && recovered == 0 && skipped == 0 && failed == 0;
  }
}
