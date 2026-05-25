package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transaction;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ConditionalOnExecutionModeEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionAuditCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionBroadcastResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorChainNonceSnapshotResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExecutionModeEnabled
public class ExecutionTransactionGatewayAdapter implements ExecutionTransactionGatewayPort {

  private final ManageExecutionTransactionUseCase manageExecutionTransactionUseCase;
  private final ManageNonceSlotLifecycleUseCase manageNonceSlotLifecycleUseCase;

  @Override
  public Optional<TransactionRecord> findById(Long transactionId) {
    return manageExecutionTransactionUseCase.findById(transactionId).map(this::toRecord);
  }

  @Override
  public TransactionRecord createAndFlush(CreateTransactionCommand command) {
    return toRecord(
        manageExecutionTransactionUseCase.createAndFlush(
            new ExecutionTransactionRecordCommand(
                command.idempotencyKey(),
                TransactionReferenceType.valueOf(command.referenceType().name()),
                command.referenceId(),
                command.fromUserId(),
                command.toUserId(),
                command.fromAddress(),
                command.toAddress(),
                command.amountWei(),
                command.chainId(),
                command.nonce(),
                TransactionStatus.valueOf(command.status().name()),
                TransactionType.valueOf(command.txType().name()),
                command.authorityAddress(),
                command.authorizationNonce(),
                command.delegateTarget(),
                command.authorizationExpiresAt())));
  }

  @Override
  public void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash) {
    manageExecutionTransactionUseCase.markSigned(transactionId, nonce, signedRawTx, txHash);
  }

  @Override
  public void markPending(Long transactionId, String txHash) {
    manageExecutionTransactionUseCase.markPending(transactionId, txHash);
  }

  @Override
  public void scheduleRetry(
      Long transactionId, String failureReason, LocalDateTime processingUntil) {
    manageExecutionTransactionUseCase.scheduleRetry(transactionId, failureReason, processingUntil);
  }

  @Override
  public SponsorNonceSnapshot loadSponsorNonceSnapshot(long chainId, String fromAddress) {
    SponsorChainNonceSnapshotResult snapshot =
        manageExecutionTransactionUseCase.loadSponsorNonceSnapshot(chainId, fromAddress);
    return new SponsorNonceSnapshot(
        snapshot.chainPendingNonce(),
        snapshot.chainLatestNonce(),
        snapshot.mainPendingNonce(),
        snapshot.subPendingNonce(),
        snapshot.mainLatestNonce(),
        snapshot.subLatestNonce());
  }

  @Override
  public SponsorNonceCoordinationRecord coordinateSponsorNonce(
      CoordinateSponsorNonceCommand command) {
    SponsorNonceCoordinationResult result =
        manageExecutionTransactionUseCase.coordinateSponsorNonce(
            new SponsorNonceCoordinationCommand(
                command.chainId(),
                command.fromAddress(),
                command.chainPendingNonce(),
                command.chainLatestNonce(),
                command.mainPendingNonce(),
                command.subPendingNonce(),
                command.mainLatestNonce(),
                command.subLatestNonce(),
                command.openWindowSize(),
                command.transactionId(),
                command.attemptIdempotencyKey(),
                command.now()));
    return new SponsorNonceCoordinationRecord(
        result.decision().type().name(),
        result.decision().nonce(),
        result.decision().reason(),
        result.reserved(),
        result.reservation() == null ? null : result.reservation().attemptId(),
        result.reservation() == null ? null : result.reservation().transactionId());
  }

  @Override
  public void transitionSponsorNonceSlot(SponsorNonceSlotTransitionCommand command) {
    manageNonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(command.nonce())
            .fromStatus(SponsorNonceSlotStatus.valueOf(command.fromStatus()))
            .toStatus(SponsorNonceSlotStatus.valueOf(command.toStatus()))
            .activeAttemptId(command.activeAttemptId())
            .activeTxId(command.activeTxId())
            .releasedAttemptId(command.releasedAttemptId())
            .releasedTxId(command.releasedTxId())
            .stateChangedAt(command.stateChangedAt())
            .releaseReason(command.releaseReason())
            .terminalReason(command.terminalReason())
            .broadcastRecoveryClaimOwner(command.broadcastRecoveryClaimOwner())
            .broadcastRecoveryClaimToken(command.broadcastRecoveryClaimToken())
            .broadcastRecoveryClaimExpiresAt(command.broadcastRecoveryClaimExpiresAt())
            .broadcastRecoveryAttemptCount(command.broadcastRecoveryAttemptCount())
            .hasRawTx(command.hasRawTx())
            .hasTxHash(command.hasTxHash())
            .hasSigningEvidence(command.hasSigningEvidence())
            .hasBroadcastEvidence(command.hasBroadcastEvidence())
            .hasReceiptEvidence(command.hasReceiptEvidence())
            .build());
  }

  @Override
  public void recordAudit(AuditCommand command) {
    manageExecutionTransactionUseCase.recordAudit(
        new ExecutionTransactionAuditCommand(
            command.transactionId(),
            TransactionAuditEventType.valueOf(command.eventType().name()),
            command.rpcAlias(),
            command.detail()));
  }

  @Override
  public BroadcastResult broadcast(String rawTx) {
    ExecutionTransactionBroadcastResult result = manageExecutionTransactionUseCase.broadcast(rawTx);
    return new BroadcastResult(
        result.success(), result.txHash(), result.failureReason(), result.rpcAlias());
  }

  private TransactionRecord toRecord(ExecutionTransactionRecordResult result) {
    return new TransactionRecord(
        result.transactionId(),
        momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus.valueOf(
            result.status().name()),
        result.txHash());
  }
}
