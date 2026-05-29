package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Batch-friendly result for future marketplace admin scheduler callers. */
public record MarketplaceAdminSchedulerExecutionResult(
    boolean processed,
    boolean skipped,
    String skipCode,
    String skipReason,
    MarketplaceAdminExecutionResult execution) {

  public MarketplaceAdminSchedulerExecutionResult {
    if (processed == skipped) {
      throw new IllegalArgumentException(
          "marketplace scheduler result must be exactly one of processed or skipped");
    }
    if (skipCode != null && skipCode.isBlank()) {
      throw new IllegalArgumentException("skipCode must be null or non-blank");
    }
    if (skipReason != null && skipReason.isBlank()) {
      throw new IllegalArgumentException("skipReason must be null or non-blank");
    }
    if (processed) {
      if (execution == null) {
        throw new IllegalArgumentException("processed scheduler result requires execution");
      }
      if (skipCode != null || skipReason != null) {
        throw new IllegalArgumentException(
            "processed scheduler result must not include skipCode or skipReason");
      }
    } else if (execution != null) {
      throw new IllegalArgumentException("skipped scheduler result must not include execution");
    }
  }

  public static MarketplaceAdminSchedulerExecutionResult processed(
      MarketplaceAdminExecutionResult execution) {
    return new MarketplaceAdminSchedulerExecutionResult(true, false, null, null, execution);
  }

  public static MarketplaceAdminSchedulerExecutionResult skipped(String skipReason) {
    return skipped(null, skipReason);
  }

  public static MarketplaceAdminSchedulerExecutionResult skipped(
      String skipCode, String skipReason) {
    return new MarketplaceAdminSchedulerExecutionResult(false, true, skipCode, skipReason, null);
  }
}
