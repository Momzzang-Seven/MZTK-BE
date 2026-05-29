package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.util.Map;
import java.util.Objects;

public record RunMarketplaceWeb3AutoSettleBatchResult(
    int scannedCount,
    int eligibleCount,
    int scheduledCount,
    int skippedCount,
    int failedCount,
    Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> skipReasonCounts,
    MarketplaceWeb3AutoSettleScanCursor nextCursor,
    int scanPages,
    boolean rawExhausted,
    boolean scanLimitReached) {

  public RunMarketplaceWeb3AutoSettleBatchResult {
    validateNonNegative(scannedCount, "scannedCount");
    validateNonNegative(eligibleCount, "eligibleCount");
    validateNonNegative(scheduledCount, "scheduledCount");
    validateNonNegative(skippedCount, "skippedCount");
    validateNonNegative(failedCount, "failedCount");
    validateNonNegative(scanPages, "scanPages");
    skipReasonCounts = Map.copyOf(Objects.requireNonNull(skipReasonCounts, "skipReasonCounts"));
    nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
  }

  public static RunMarketplaceWeb3AutoSettleBatchResult empty(
      int scannedCount,
      MarketplaceWeb3AutoSettleScanCursor nextCursor,
      int scanPages,
      boolean rawExhausted,
      boolean scanLimitReached) {
    return new RunMarketplaceWeb3AutoSettleBatchResult(
        scannedCount, 0, 0, 0, 0, Map.of(), nextCursor, scanPages, rawExhausted, scanLimitReached);
  }

  private static void validateNonNegative(int value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative");
    }
  }
}
