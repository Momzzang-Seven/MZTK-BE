package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;

public interface ExecutionTransactionGatewayPort {

  Optional<TransferTransaction> findById(Long transactionId);

  TransferTransaction createAndFlush(TransferTransaction transaction);

  void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash);

  void markPending(Long transactionId, String txHash);

  void scheduleRetry(Long transactionId, String failureReason, LocalDateTime processingUntil);

  long reserveNextNonce(String fromAddress);

  void recordAudit(AuditCommand command);

  BroadcastResult broadcast(String rawTx);

  record AuditCommand(
      Long transactionId,
      Web3TransactionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {}

  record BroadcastResult(boolean success, String txHash, String failureReason, String rpcAlias) {}
}
