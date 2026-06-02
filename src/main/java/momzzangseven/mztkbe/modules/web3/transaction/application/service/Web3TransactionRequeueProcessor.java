package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TransactionRequeueItemResultType;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ManageTransactionRecoveryPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RunTransactionStateUpdatePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
class Web3TransactionRequeueProcessor {

  private final ManageTransactionRecoveryPort manageTransactionRecoveryPort;
  private final LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final RunTransactionStateUpdatePort runTransactionStateUpdatePort;
  private final Clock appClock;

  RequeueWeb3TransactionResult requeueOrThrow(RequeueWeb3TransactionCommand command) {
    try {
      return runTransactionStateUpdatePort.requiresNew(() -> requeueSingleInTransaction(command));
    } catch (Web3InvalidInputException | Web3TransactionStateInvalidException e) {
      recordSingleRejectedAudit(command, e);
      throw e;
    }
  }

  BulkRequeueWeb3TransactionItemResult requeueForBulk(
      Long operatorId, Long transactionId, String reason, String evidence) {
    try {
      return runTransactionStateUpdatePort.requiresNew(
          () -> requeueBulkItemInTransaction(operatorId, transactionId, reason, evidence));
    } catch (RuntimeException e) {
      return new BulkRequeueWeb3TransactionItemResult(
          transactionId,
          TransactionRequeueItemResultType.FAILED,
          null,
          null,
          null,
          e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private RequeueWeb3TransactionResult requeueSingleInTransaction(
      RequeueWeb3TransactionCommand command) {
    ManageTransactionRecoveryPort.RecoverySnapshot snapshot =
        loadSnapshotForUpdate(command.transactionId());
    validateTreasurySignerPreflight(snapshot);

    ManageTransactionRecoveryPort.RequeueMutation mutation =
        manageTransactionRecoveryPort.clearFailureForRequeue(command.transactionId());
    LocalDateTime now = LocalDateTime.now(appClock);
    recordTransactionAuditPort.record(
        new RecordTransactionAuditPort.AuditCommand(
            command.transactionId(),
            Web3TransactionAuditEventType.REQUEUE,
            null,
            requeueAuditDetail(
                command.operatorId(),
                command.reason(),
                command.evidence(),
                mutation.previousStatus().name(),
                mutation.originalFailureReason(),
                mutation.status().name(),
                "REQUEUED",
                now,
                null,
                null)));

    return new RequeueWeb3TransactionResult(
        mutation.transactionId(),
        mutation.status(),
        mutation.previousStatus(),
        mutation.originalFailureReason(),
        true);
  }

  private BulkRequeueWeb3TransactionItemResult requeueBulkItemInTransaction(
      Long operatorId, Long transactionId, String reason, String evidence) {
    ManageTransactionRecoveryPort.RecoverySnapshot snapshot =
        manageTransactionRecoveryPort.loadByIdForUpdate(transactionId).orElse(null);
    if (snapshot == null) {
      return new BulkRequeueWeb3TransactionItemResult(
          transactionId,
          TransactionRequeueItemResultType.NOT_FOUND,
          null,
          null,
          null,
          "web3 transaction not found");
    }

    try {
      validateTreasurySignerPreflight(snapshot);
      ManageTransactionRecoveryPort.RequeueMutation mutation =
          manageTransactionRecoveryPort.clearFailureForRequeue(transactionId);
      LocalDateTime now = LocalDateTime.now(appClock);
      recordTransactionAuditPort.record(
          new RecordTransactionAuditPort.AuditCommand(
              transactionId,
              Web3TransactionAuditEventType.REQUEUE,
              null,
              requeueAuditDetail(
                  operatorId,
                  reason,
                  evidence,
                  mutation.previousStatus().name(),
                  mutation.originalFailureReason(),
                  mutation.status().name(),
                  "REQUEUED",
                  now,
                  null,
                  null)));
      return new BulkRequeueWeb3TransactionItemResult(
          transactionId,
          TransactionRequeueItemResultType.REQUEUED,
          mutation.status(),
          mutation.previousStatus(),
          mutation.originalFailureReason(),
          null);
    } catch (Web3InvalidInputException | Web3TransactionStateInvalidException e) {
      LocalDateTime now = LocalDateTime.now(appClock);
      recordTransactionAuditPort.record(
          new RecordTransactionAuditPort.AuditCommand(
              transactionId,
              Web3TransactionAuditEventType.REQUEUE,
              null,
              requeueAuditDetail(
                  operatorId,
                  reason,
                  evidence,
                  snapshot.status().name(),
                  snapshot.failureReason(),
                  snapshot.status().name(),
                  "REJECTED",
                  null,
                  now,
                  e.getMessage())));
      return new BulkRequeueWeb3TransactionItemResult(
          transactionId,
          TransactionRequeueItemResultType.REJECTED,
          snapshot.status(),
          snapshot.status(),
          snapshot.failureReason(),
          e.getMessage());
    }
  }

  private ManageTransactionRecoveryPort.RecoverySnapshot loadSnapshotForUpdate(Long transactionId) {
    return manageTransactionRecoveryPort
        .loadByIdForUpdate(transactionId)
        .orElseThrow(() -> new Web3TransactionNotFoundException(transactionId));
  }

  private void recordSingleRejectedAudit(
      RequeueWeb3TransactionCommand command, RuntimeException exception) {
    try {
      ManageTransactionRecoveryPort.RecoverySnapshot snapshot =
          manageTransactionRecoveryPort.loadByIdForUpdate(command.transactionId()).orElse(null);
      if (snapshot == null) {
        return;
      }

      LocalDateTime now = LocalDateTime.now(appClock);
      recordTransactionAuditPort.record(
          new RecordTransactionAuditPort.AuditCommand(
              command.transactionId(),
              Web3TransactionAuditEventType.REQUEUE,
              null,
              requeueAuditDetail(
                  command.operatorId(),
                  command.reason(),
                  command.evidence(),
                  snapshot.status().name(),
                  snapshot.failureReason(),
                  snapshot.status().name(),
                  "REJECTED",
                  null,
                  now,
                  exception.getMessage())));
    } catch (RuntimeException auditException) {
      log.warn(
          "Failed to record rejected web3 transaction requeue audit: transactionId={},"
              + " originalException={}",
          command.transactionId(),
          exception.getMessage(),
          auditException);
    }
  }

  private void validateTreasurySignerPreflight(
      ManageTransactionRecoveryPort.RecoverySnapshot snapshot) {
    if (!Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code().equals(snapshot.failureReason())) {
      return;
    }

    var rewardWallet =
        loadRewardTreasuryWalletPort
            .load()
            .orElseThrow(
                () -> new Web3TransactionStateInvalidException("reward treasury wallet missing"));

    if (!rewardWallet.active()) {
      throw new Web3TransactionStateInvalidException("reward treasury wallet inactive");
    }
    if (rewardWallet.walletAddress() == null || rewardWallet.walletAddress().isBlank()) {
      throw new Web3TransactionStateInvalidException("reward treasury wallet address missing");
    }
    if (snapshot.fromAddress() == null || snapshot.fromAddress().isBlank()) {
      throw new Web3TransactionStateInvalidException("transaction fromAddress missing");
    }

    String treasuryAddress = normalizeAddress(rewardWallet.walletAddress(), "reward treasury");
    String fromAddress = normalizeAddress(snapshot.fromAddress(), "transaction fromAddress");
    if (!treasuryAddress.equals(fromAddress)) {
      throw new Web3TransactionStateInvalidException(
          "treasury signer still mismatched: current="
              + treasuryAddress
              + ", expected="
              + fromAddress);
    }
  }

  private String normalizeAddress(String rawAddress, String fieldName) {
    try {
      return EvmAddress.of(rawAddress).value();
    } catch (Web3InvalidInputException e) {
      throw new Web3TransactionStateInvalidException(fieldName + " invalid: " + e.getMessage(), e);
    }
  }

  private Map<String, Object> requeueAuditDetail(
      Long operatorId,
      String reason,
      String evidence,
      String originalStatus,
      String originalFailureReason,
      String newStatus,
      String result,
      LocalDateTime requeuedAt,
      LocalDateTime rejectedAt,
      String rejectionReason) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("operatorId", operatorId);
    detail.put("reason", reason);
    detail.put("evidence", evidence);
    detail.put("originalStatus", originalStatus);
    detail.put("originalFailureReason", originalFailureReason);
    detail.put("newStatus", newStatus);
    detail.put("result", result);
    detail.put("requeuedAt", requeuedAt);
    detail.put("rejectedAt", rejectedAt);
    if (rejectionReason != null && !rejectionReason.isBlank()) {
      detail.put("rejectionReason", rejectionReason);
    }
    return detail;
  }
}
