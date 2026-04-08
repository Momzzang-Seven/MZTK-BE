package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionAuditCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionBroadcastResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionSummaryResult;

public interface ManageExecutionTransactionUseCase {

  Optional<ExecutionTransactionRecordResult> findById(Long transactionId);

  Optional<ExecutionTransactionSummaryResult> findSummaryById(Long transactionId);

  ExecutionTransactionRecordResult createAndFlush(ExecutionTransactionRecordCommand command);

  void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash);

  void markPending(Long transactionId, String txHash);

  void scheduleRetry(Long transactionId, String failureReason, LocalDateTime processingUntil);

  long reserveNextNonce(String fromAddress);

  void recordAudit(ExecutionTransactionAuditCommand command);

  ExecutionTransactionBroadcastResult broadcast(String rawTx);
}
