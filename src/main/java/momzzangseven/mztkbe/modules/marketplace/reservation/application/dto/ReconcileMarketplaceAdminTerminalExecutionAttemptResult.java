package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Result for one marketplace admin terminal hook reconciliation batch. */
public record ReconcileMarketplaceAdminTerminalExecutionAttemptResult(
    int scanned, int replayed, int skipped, int failed) {

  public boolean isEmpty() {
    return scanned == 0 && replayed == 0 && skipped == 0 && failed == 0;
  }
}
