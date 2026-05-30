package momzzangseven.mztkbe.modules.level.application.dto;

/**
 * Outcome of one reconciliation pass.
 *
 * @param scanned rows examined
 * @param granted rows successfully granted and marked DONE
 * @param skipped rows held by another worker or no longer PENDING
 * @param failed rows whose grant threw and were rescheduled or marked FAILED
 */
public record RunXpGrantReconciliationResult(int scanned, int granted, int skipped, int failed) {}
