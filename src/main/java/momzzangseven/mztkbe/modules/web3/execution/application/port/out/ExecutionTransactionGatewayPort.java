package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

public interface ExecutionTransactionGatewayPort {

  Optional<TransactionRecord> findById(Long transactionId);

  TransactionRecord createAndFlush(CreateTransactionCommand command);

  void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash);

  void markPending(Long transactionId, String txHash);

  void scheduleRetry(Long transactionId, String failureReason, LocalDateTime processingUntil);

  long reserveNextNonce(String fromAddress);

  void recordAudit(AuditCommand command);

  BroadcastResult broadcast(String rawTx);

  record AuditCommand(
      Long transactionId, String eventType, String rpcAlias, Map<String, Object> detail) {}

  record BroadcastResult(boolean success, String txHash, String failureReason, String rpcAlias) {}

  record TransactionRecord(Long transactionId, String status, String txHash) {}

  record CreateTransactionCommand(
      String idempotencyKey,
      String referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      Long nonce,
      String status,
      String txType,
      String authorityAddress,
      Long authorizationNonce,
      String delegateTarget,
      LocalDateTime authorizationExpiresAt) {}
}
