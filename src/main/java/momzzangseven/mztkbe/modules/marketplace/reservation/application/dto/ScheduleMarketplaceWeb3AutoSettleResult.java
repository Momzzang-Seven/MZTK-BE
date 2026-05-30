package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.util.Objects;

public record ScheduleMarketplaceWeb3AutoSettleResult(
    Outcome outcome, MarketplaceWeb3AutoSettleSkipCategory skipCategory, String skipReason) {

  public enum Outcome {
    SCHEDULED,
    SKIPPED
  }

  public ScheduleMarketplaceWeb3AutoSettleResult {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(skipCategory, "skipCategory");
    if (outcome == Outcome.SCHEDULED) {
      if (skipCategory != MarketplaceWeb3AutoSettleSkipCategory.NONE || skipReason != null) {
        throw new IllegalArgumentException(
            "scheduled result must use NONE skipCategory and null skipReason");
      }
    }
    if (outcome == Outcome.SKIPPED && skipCategory == MarketplaceWeb3AutoSettleSkipCategory.NONE) {
      throw new IllegalArgumentException("skipped result must use a non-NONE skipCategory");
    }
  }

  public static ScheduleMarketplaceWeb3AutoSettleResult scheduled() {
    return new ScheduleMarketplaceWeb3AutoSettleResult(
        Outcome.SCHEDULED, MarketplaceWeb3AutoSettleSkipCategory.NONE, null);
  }

  public static ScheduleMarketplaceWeb3AutoSettleResult skipped(
      MarketplaceWeb3AutoSettleSkipCategory skipCategory, String skipReason) {
    return new ScheduleMarketplaceWeb3AutoSettleResult(Outcome.SKIPPED, skipCategory, skipReason);
  }
}
