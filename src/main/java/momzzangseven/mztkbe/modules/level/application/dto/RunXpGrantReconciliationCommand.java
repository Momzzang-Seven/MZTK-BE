package momzzangseven.mztkbe.modules.level.application.dto;

/**
 * Input for one reconciliation pass over the XP-grant outbox.
 *
 * @param batchSize max rows to scan in this pass
 * @param maxAttempts attempts after which a row is marked FAILED
 * @param backoffSeconds base linear backoff between retries (multiplied by attempt count)
 */
public record RunXpGrantReconciliationCommand(int batchSize, int maxAttempts, int backoffSeconds) {}
