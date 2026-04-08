package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionAuditCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionBroadcastResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.ExecutionTransactionRecordResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionTransactionGatewayAdapter implements ExecutionTransactionGatewayPort {

  private final ManageExecutionTransactionUseCase manageExecutionTransactionUseCase;

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
                command.referenceType(),
                command.referenceId(),
                command.fromUserId(),
                command.toUserId(),
                command.fromAddress(),
                command.toAddress(),
                command.amountWei(),
                command.nonce(),
                command.status(),
                command.txType(),
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
  public long reserveNextNonce(String fromAddress) {
    return manageExecutionTransactionUseCase.reserveNextNonce(fromAddress);
  }

  @Override
  public void recordAudit(AuditCommand command) {
    manageExecutionTransactionUseCase.recordAudit(
        new ExecutionTransactionAuditCommand(
            command.transactionId(), command.eventType(), command.rpcAlias(), command.detail()));
  }

  @Override
  public BroadcastResult broadcast(String rawTx) {
    ExecutionTransactionBroadcastResult result = manageExecutionTransactionUseCase.broadcast(rawTx);
    return new BroadcastResult(
        result.success(), result.txHash(), result.failureReason(), result.rpcAlias());
  }

  private TransactionRecord toRecord(ExecutionTransactionRecordResult result) {
    return new TransactionRecord(result.transactionId(), result.status(), result.txHash());
  }
}
