package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail.CsOverrideAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class MarkTransactionSucceededService implements MarkTransactionSucceededUseCase {

  private final LoadTransactionPort loadTransactionPort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final Web3ContractPort web3ContractPort;

  @Override
  @AdminOnly(
      actionType = "TRANSACTION_MARK_SUCCEEDED",
      targetType = "WEB3_TRANSACTION",
      operatorId = "#command.operatorId()",
      targetId = "#command.transactionId()")
  public MarkTransactionSucceededResult execute(MarkTransactionSucceededCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }

    command.validate();

    LoadTransactionPort.TransactionSnapshot snapshot =
        loadTransactionPort
            .loadById(command.transactionId())
            .orElseThrow(() -> new Web3TransactionNotFoundException(command.transactionId()));

    if (snapshot.status() != Web3TxStatus.UNCONFIRMED) {
      throw new Web3TransactionStateInvalidException(
          "only UNCONFIRMED can be overridden: currentStatus=" + snapshot.status());
    }
    if (snapshot.txHash() != null && !snapshot.txHash().equalsIgnoreCase(command.txHash())) {
      throw new Web3TransactionStateInvalidException(
          "txHash mismatch: existing=" + snapshot.txHash() + ", requested=" + command.txHash());
    }

    Web3ContractPort.ReceiptResult receipt = web3ContractPort.getReceipt(command.txHash());
    if (receipt.rpcError()) {
      throw new Web3TransactionStateInvalidException(
          "receipt lookup failed: " + receipt.failureReason());
    }
    if (!receipt.found() || !Boolean.TRUE.equals(receipt.success())) {
      throw new Web3TransactionStateInvalidException(
          "receipt proof is required (receipt.status == 1)");
    }

    updateTransactionPort.updateStatus(
        command.transactionId(), Web3TxStatus.SUCCEEDED, command.txHash(), null);
    recordTransactionAuditPort.record(
        new RecordTransactionAuditPort.AuditCommand(
            command.transactionId(),
            Web3TransactionAuditEventType.CS_OVERRIDE,
            receipt.rpcAlias(),
            new CsOverrideAuditDetail(
                    command.operatorId(),
                    snapshot.status(),
                    Web3TxStatus.SUCCEEDED,
                    command.reason(),
                    command.evidence(),
                    command.explorerUrl(),
                    command.txHash(),
                    receipt.found(),
                    receipt.success(),
                    receipt.failureReason())
                .toMap()));

    return MarkTransactionSucceededResult.builder()
        .transactionId(command.transactionId())
        .status(Web3TxStatus.SUCCEEDED)
        .previousStatus(snapshot.status())
        .txHash(command.txHash())
        .explorerUrl(command.explorerUrl())
        .build();
  }
}
