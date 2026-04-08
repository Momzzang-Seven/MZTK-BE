package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionAuditEventType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionType;

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
      Long transactionId,
      ExecutionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {}

  record BroadcastResult(boolean success, String txHash, String failureReason, String rpcAlias) {}

  record TransactionRecord(Long transactionId, ExecutionTransactionStatus status, String txHash) {}

  record CreateTransactionCommand(
      String idempotencyKey,
      ExecutionReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      Long nonce,
      ExecutionTransactionStatus status,
      ExecutionTransactionType txType,
      String authorityAddress,
      Long authorizationNonce,
      String delegateTarget,
      LocalDateTime authorizationExpiresAt) {}
}
