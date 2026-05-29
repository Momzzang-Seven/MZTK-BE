package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.util.Map;
import java.util.Objects;

public record RunMarketplaceWeb3AutoSettleResult(
    int batchesRun,
    int scannedCount,
    int eligibleCount,
    int scheduledCount,
    int skippedCount,
    int failedCount,
    Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> skipReasonCounts,
    int scanPages,
    boolean rawExhausted,
    boolean scanLimitReached) {

  public RunMarketplaceWeb3AutoSettleResult {
    validateNonNegative(batchesRun, "batchesRun");
    validateNonNegative(scannedCount, "scannedCount");
    validateNonNegative(eligibleCount, "eligibleCount");
    validateNonNegative(scheduledCount, "scheduledCount");
    validateNonNegative(skippedCount, "skippedCount");
    validateNonNegative(failedCount, "failedCount");
    validateNonNegative(scanPages, "scanPages");
    skipReasonCounts = Map.copyOf(Objects.requireNonNull(skipReasonCounts, "skipReasonCounts"));
  }

  private static void validateNonNegative(int value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative");
    }
  }
}
