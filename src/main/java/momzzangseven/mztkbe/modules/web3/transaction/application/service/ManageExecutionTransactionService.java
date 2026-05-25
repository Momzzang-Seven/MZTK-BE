package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionAuditCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionBroadcastResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionSummaryResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorChainNonceSnapshotResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorChainNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class ManageExecutionTransactionService implements ManageExecutionTransactionUseCase {

  private final TransferTransactionPersistencePort transferTransactionPersistencePort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final Web3ContractPort web3ContractPort;
  private final LoadSponsorChainNoncePort loadSponsorChainNoncePort;
  private final CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase;

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionTransactionRecordResult> findById(Long transactionId) {
    return transferTransactionPersistencePort.findById(transactionId).map(this::toResult);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionTransactionSummaryResult> findSummaryById(Long transactionId) {
    return transferTransactionPersistencePort
        .findById(transactionId)
        .map(
            transaction ->
                new ExecutionTransactionSummaryResult(
                    transaction.getId(),
                    TransactionStatus.valueOf(transaction.getStatus().name()),
                    transaction.getTxHash()));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, ExecutionTransactionSummaryResult> findSummariesByIds(
      Collection<Long> transactionIds) {
    if (transactionIds == null || transactionIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, ExecutionTransactionSummaryResult> results = new LinkedHashMap<>();
    for (TransferTransaction transaction :
        transferTransactionPersistencePort.findByIds(transactionIds)) {
      results.put(
          transaction.getId(),
          new ExecutionTransactionSummaryResult(
              transaction.getId(),
              TransactionStatus.valueOf(transaction.getStatus().name()),
              transaction.getTxHash()));
    }
    return results;
  }

  @Override
  public ExecutionTransactionRecordResult createAndFlush(
      ExecutionTransactionRecordCommand command) {
    TransferTransaction transaction =
        transferTransactionPersistencePort.createAndFlush(
            TransferTransaction.builder()
                .idempotencyKey(command.idempotencyKey())
                .referenceType(Web3ReferenceType.valueOf(command.referenceType().name()))
                .referenceId(command.referenceId())
                .fromUserId(command.fromUserId())
                .toUserId(command.toUserId())
                .fromAddress(command.fromAddress())
                .toAddress(command.toAddress())
                .amountWei(command.amountWei())
                .chainId(command.chainId())
                .nonce(command.nonce())
                .status(Web3TxStatus.valueOf(command.status().name()))
                .txType(Web3TxType.valueOf(command.txType().name()))
                .authorityAddress(command.authorityAddress())
                .authorizationNonce(command.authorizationNonce())
                .delegateTarget(command.delegateTarget())
                .authorizationExpiresAt(command.authorizationExpiresAt())
                .build());
    return toResult(transaction);
  }

  @Override
  public void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash) {
    updateTransactionPort.markSigned(transactionId, nonce, signedRawTx, txHash);
  }

  @Override
  public void markPending(Long transactionId, String txHash) {
    updateTransactionPort.markPending(transactionId, txHash);
  }

  @Override
  public void scheduleRetry(
      Long transactionId, String failureReason, LocalDateTime processingUntil) {
    updateTransactionPort.scheduleRetry(transactionId, failureReason, processingUntil);
  }

  @Override
  public boolean claimSignedForBroadcast(
      Long transactionId, String workerId, LocalDateTime processingUntil) {
    return updateTransactionPort.claimForProcessing(
        transactionId, Web3TxStatus.SIGNED, workerId, processingUntil);
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SponsorChainNonceSnapshotResult loadSponsorNonceSnapshot(
      long chainId, String fromAddress) {
    LoadSponsorChainNoncePort.SponsorChainNonceSnapshot snapshot =
        loadSponsorChainNoncePort.loadSnapshot(chainId, fromAddress);
    return new SponsorChainNonceSnapshotResult(
        snapshot.chainPendingNonce(),
        snapshot.chainLatestNonce(),
        snapshot.mainPendingNonce(),
        snapshot.subPendingNonce(),
        snapshot.mainLatestNonce(),
        snapshot.subLatestNonce());
  }

  @Override
  public SponsorNonceCoordinationResult coordinateSponsorNonce(
      SponsorNonceCoordinationCommand command) {
    return coordinateSponsorNonceUseCase.execute(command);
  }

  @Override
  public void recordAudit(ExecutionTransactionAuditCommand command) {
    recordTransactionAuditPort.record(
        new RecordTransactionAuditPort.AuditCommand(
            command.transactionId(),
            Web3TransactionAuditEventType.valueOf(command.eventType().name()),
            command.rpcAlias(),
            command.detail()));
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ExecutionTransactionBroadcastResult broadcast(String rawTx) {
    Web3ContractPort.BroadcastResult result =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(rawTx));
    return new ExecutionTransactionBroadcastResult(
        result.success(), result.txHash(), result.failureReason(), result.rpcAlias());
  }

  private ExecutionTransactionRecordResult toResult(TransferTransaction transaction) {
    return new ExecutionTransactionRecordResult(
        transaction.getId(),
        TransactionStatus.valueOf(transaction.getStatus().name()),
        transaction.getTxHash(),
        transaction.getChainId(),
        transaction.getFromAddress(),
        transaction.getNonce());
  }
}
