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

  SponsorNonceSnapshot loadSponsorNonceSnapshot(long chainId, String fromAddress);

  SponsorNonceCoordinationRecord coordinateSponsorNonce(CoordinateSponsorNonceCommand command);

  void transitionSponsorNonceSlot(SponsorNonceSlotTransitionCommand command);

  void recordAudit(AuditCommand command);

  BroadcastResult broadcast(String rawTx);

  record AuditCommand(
      Long transactionId,
      ExecutionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {}

  record BroadcastResult(boolean success, String txHash, String failureReason, String rpcAlias) {}

  record TransactionRecord(
      Long transactionId,
      ExecutionTransactionStatus status,
      String txHash,
      Long chainId,
      String fromAddress,
      Long nonce) {

    public TransactionRecord(Long transactionId, ExecutionTransactionStatus status, String txHash) {
      this(transactionId, status, txHash, null, null, null);
    }
  }

  record SponsorNonceSnapshot(
      long chainPendingNonce,
      long chainLatestNonce,
      Long mainPendingNonce,
      Long subPendingNonce,
      Long mainLatestNonce,
      Long subLatestNonce) {}

  record CoordinateSponsorNonceCommand(
      long chainId,
      String fromAddress,
      long chainPendingNonce,
      long chainLatestNonce,
      Long mainPendingNonce,
      Long subPendingNonce,
      Long mainLatestNonce,
      Long subLatestNonce,
      int openWindowSize,
      Long transactionId,
      String attemptIdempotencyKey,
      LocalDateTime now) {}

  record SponsorNonceCoordinationRecord(
      String decisionType,
      Long nonce,
      String reason,
      boolean reserved,
      Long attemptId,
      Long transactionId) {}

  record SponsorNonceSlotTransitionCommand(
      long chainId,
      String fromAddress,
      long nonce,
      String fromStatus,
      String toStatus,
      Long activeAttemptId,
      Long activeTxId,
      Long releasedAttemptId,
      Long releasedTxId,
      LocalDateTime stateChangedAt,
      String releaseReason,
      String terminalReason,
      String broadcastRecoveryClaimOwner,
      String broadcastRecoveryClaimToken,
      LocalDateTime broadcastRecoveryClaimExpiresAt,
      int broadcastRecoveryAttemptCount,
      boolean hasRawTx,
      boolean hasTxHash,
      boolean hasSigningEvidence,
      boolean hasBroadcastEvidence,
      boolean hasReceiptEvidence) {}

  record CreateTransactionCommand(
      String idempotencyKey,
      ExecutionReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      Long chainId,
      Long nonce,
      ExecutionTransactionStatus status,
      ExecutionTransactionType txType,
      String authorityAddress,
      Long authorizationNonce,
      String delegateTarget,
      LocalDateTime authorizationExpiresAt) {}
}
