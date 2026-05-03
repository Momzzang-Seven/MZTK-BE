package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionAuditCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionBroadcastResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionSummaryResult;

public interface ManageExecutionTransactionUseCase {

  Optional<ExecutionTransactionRecordResult> findById(Long transactionId);

  Optional<ExecutionTransactionSummaryResult> findSummaryById(Long transactionId);

  default Map<Long, ExecutionTransactionSummaryResult> findSummariesByIds(
      Collection<Long> transactionIds) {
    Map<Long, ExecutionTransactionSummaryResult> results = new LinkedHashMap<>();
    for (Long transactionId : transactionIds) {
      findSummaryById(transactionId).ifPresent(summary -> results.put(transactionId, summary));
    }
    return results;
  }

  ExecutionTransactionRecordResult createAndFlush(ExecutionTransactionRecordCommand command);

  void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash);

  void markPending(Long transactionId, String txHash);

  void scheduleRetry(Long transactionId, String failureReason, LocalDateTime processingUntil);

  long reserveNextNonce(String fromAddress);

  /**
   * Atomic CAS release of a previously reserved nonce. Returns {@code true} when the cursor was
   * rolled back from {@code reservedNonce + 1} to {@code reservedNonce}; {@code false} when another
   * reservation has already advanced the cursor past it (the gap is unrecoverable here — the caller
   * must surface a {@code NONCE_GAP_DETECTED} alert per PR #150 follow-up F-1).
   *
   * <p>Exposed so sibling web3 modules (execution, ...) that reserve a nonce via {@link
   * #reserveNextNonce(String)} but fail before broadcast can release the cursor without leaking it.
   */
  boolean releaseReservedNonce(String fromAddress, long reservedNonce);

  long loadPendingNonce(String fromAddress);

  void recordAudit(ExecutionTransactionAuditCommand command);

  ExecutionTransactionBroadcastResult broadcast(String rawTx);
}
