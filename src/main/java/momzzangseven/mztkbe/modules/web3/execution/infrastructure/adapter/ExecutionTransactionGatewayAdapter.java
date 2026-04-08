package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionTransactionGatewayAdapter implements ExecutionTransactionGatewayPort {

  private final TransferTransactionPersistencePort transferTransactionPersistencePort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;

  @Override
  public Optional<TransferTransaction> findById(Long transactionId) {
    return transferTransactionPersistencePort.findById(transactionId);
  }

  @Override
  public TransferTransaction createAndFlush(TransferTransaction transaction) {
    return transferTransactionPersistencePort.createAndFlush(transaction);
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
  public long reserveNextNonce(String fromAddress) {
    return reserveNoncePort.reserveNextNonce(fromAddress);
  }

  @Override
  public void recordAudit(AuditCommand command) {
    recordTransactionAuditPort.record(
        new RecordTransactionAuditPort.AuditCommand(
            command.transactionId(), command.eventType(), command.rpcAlias(), command.detail()));
  }

  @Override
  public BroadcastResult broadcast(String rawTx) {
    Web3ContractPort.BroadcastResult result =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(rawTx));
    return new BroadcastResult(
        result.success(), result.txHash(), result.failureReason(), result.rpcAlias());
  }
}
