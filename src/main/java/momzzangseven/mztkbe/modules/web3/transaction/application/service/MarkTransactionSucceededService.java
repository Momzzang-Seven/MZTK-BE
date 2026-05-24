package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
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
  private final TransactionOutcomePublisher transactionOutcomePublisher;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final Web3ContractPort web3ContractPort;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final Clock appClock;

  @Override
  @AdminOnly(
      actionType = "TRANSACTION_MARK_SUCCEEDED",
      targetType = AuditTargetType.WEB3_TRANSACTION,
      operatorId = "#command.operatorId()",
      targetId = "#command.transactionId()")
  public MarkTransactionSucceededResult execute(MarkTransactionSucceededCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.COMMAND_REQUIRED);
    }

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

    markNonceSlotConsumed(snapshot);
    transactionOutcomePublisher.markSucceededAndPublish(
        command.transactionId(),
        snapshot.idempotencyKey(),
        snapshot.referenceType(),
        snapshot.referenceId(),
        snapshot.fromUserId(),
        snapshot.toUserId(),
        command.txHash());
    recordTransactionAuditPort.record(
        new RecordTransactionAuditPort.AuditCommand(
            command.transactionId(),
            Web3TransactionAuditEventType.CS_OVERRIDE,
            receipt.rpcAlias(),
            csOverrideAuditDetail(command, snapshot, receipt)));

    return MarkTransactionSucceededResult.builder()
        .transactionId(command.transactionId())
        .status(Web3TxStatus.SUCCEEDED)
        .previousStatus(snapshot.status())
        .txHash(command.txHash())
        .explorerUrl(command.explorerUrl())
        .build();
  }

  private void markNonceSlotConsumed(LoadTransactionPort.TransactionSnapshot snapshot) {
    if (snapshot.chainId() == null || snapshot.fromAddress() == null || snapshot.nonce() == null) {
      log.warn(
          "Skipping nonce slot consumed transition for admin override without slot scope: txId={}",
          snapshot.transactionId());
      return;
    }
    Web3TransactionStateInvalidException lastStaleException = null;
    for (SponsorNonceSlotStatus fromStatus :
        List.of(
            SponsorNonceSlotStatus.STUCK,
            SponsorNonceSlotStatus.BROADCASTED,
            SponsorNonceSlotStatus.BROADCASTING,
            SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)) {
      try {
        nonceSlotLifecycleUseCase.transition(
            RecordSponsorNonceSlotTransitionCommand.builder()
                .chainId(snapshot.chainId())
                .fromAddress(snapshot.fromAddress())
                .nonce(snapshot.nonce())
                .fromStatus(fromStatus)
                .toStatus(SponsorNonceSlotStatus.CONSUMED)
                .activeTxId(snapshot.transactionId())
                .consumedTxId(snapshot.transactionId())
                .stateChangedAt(LocalDateTime.now(appClock))
                .consumedReason("ADMIN_RECEIPT_STATUS_1")
                .hasRawTx(false)
                .hasTxHash(true)
                .hasSigningEvidence(true)
                .hasBroadcastEvidence(true)
                .hasReceiptEvidence(true)
                .build());
        return;
      } catch (Web3TransactionStateInvalidException e) {
        if (isSlotNotFound(e) || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED)) {
          log.warn(
              "Skipping nonce slot consumed transition for admin override txId={}: {}",
              snapshot.transactionId(),
              e.getMessage());
          return;
        }
        if (isStaleTransition(e)) {
          lastStaleException = e;
          continue;
        }
        throw e;
      }
    }
    if (lastStaleException != null) {
      throw lastStaleException;
    }
  }

  private boolean isSlotNotFound(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("nonce slot not found");
  }

  private boolean isStaleTransition(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("stale nonce slot transition");
  }

  private boolean isStaleActual(
      Web3TransactionStateInvalidException e, SponsorNonceSlotStatus actualStatus) {
    return isStaleTransition(e) && e.getMessage().contains("actual=" + actualStatus);
  }

  private Map<String, Object> csOverrideAuditDetail(
      MarkTransactionSucceededCommand command,
      LoadTransactionPort.TransactionSnapshot snapshot,
      Web3ContractPort.ReceiptResult receipt) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("operatorId", command.operatorId());
    detail.put("fromStatus", snapshot.status().name());
    detail.put("toStatus", Web3TxStatus.SUCCEEDED.name());
    detail.put("reason", command.reason());
    detail.put("evidence", command.evidence());
    detail.put("explorerUrl", command.explorerUrl());
    detail.put("txHash", command.txHash());
    detail.put("receiptFound", receipt.found());
    detail.put("receiptSuccess", receipt.success());
    detail.put("receiptFailureReason", receipt.failureReason());
    return detail;
  }
}
