package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Batch-friendly result for future marketplace admin scheduler callers. */
public record MarketplaceAdminSchedulerExecutionResult(
    boolean processed,
    boolean skipped,
    String skipReason,
    MarketplaceAdminExecutionResult execution) {

  public static MarketplaceAdminSchedulerExecutionResult processed(
      MarketplaceAdminExecutionResult execution) {
    return new MarketplaceAdminSchedulerExecutionResult(true, false, null, execution);
  }

  public static MarketplaceAdminSchedulerExecutionResult skipped(String skipReason) {
    return new MarketplaceAdminSchedulerExecutionResult(false, true, skipReason, null);
  }
}
