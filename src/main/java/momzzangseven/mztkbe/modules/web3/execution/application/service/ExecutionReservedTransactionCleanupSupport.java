package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

@Slf4j
final class ExecutionReservedTransactionCleanupSupport {

  private ExecutionReservedTransactionCleanupSupport() {}

  static void cleanupCreatedSubmittedTransaction(
      ExecutionTransactionGatewayPort transactionGatewayPort,
      Long submittedTxId,
      String failureReason,
      Clock appClock) {
    cleanupCreatedSubmittedTransaction(
        transactionGatewayPort, submittedTxId, failureReason, LocalDateTime.now(appClock));
  }

  static void cleanupCreatedSubmittedTransaction(
      ExecutionTransactionGatewayPort transactionGatewayPort,
      Long submittedTxId,
      String failureReason,
      LocalDateTime stateChangedAt) {
    if (submittedTxId == null || submittedTxId <= 0) {
      return;
    }
    transactionGatewayPort
        .findById(submittedTxId)
        .filter(transaction -> transaction.status() == ExecutionTransactionStatus.CREATED)
        .ifPresent(
            transaction ->
                cleanupCreatedTransaction(
                    transactionGatewayPort, transaction, failureReason, stateChangedAt));
  }

  private static void cleanupCreatedTransaction(
      ExecutionTransactionGatewayPort transactionGatewayPort,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      String failureReason,
      LocalDateTime stateChangedAt) {
    transactionGatewayPort.scheduleRetry(transaction.transactionId(), failureReason, null);
    if (transaction.chainId() == null
        || transaction.fromAddress() == null
        || transaction.nonce() == null) {
      return;
    }

    try {
      transactionGatewayPort.transitionSponsorNonceSlot(
          new ExecutionTransactionGatewayPort.SponsorNonceSlotTransitionCommand(
              transaction.chainId(),
              transaction.fromAddress(),
              transaction.nonce(),
              "RESERVED",
              "DROPPED",
              null,
              transaction.transactionId(),
              null,
              transaction.transactionId(),
              stateChangedAt,
              failureReason,
              null,
              null,
              null,
              null,
              0,
              false,
              false,
              false,
              false,
              false));
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e) || isStaleActual(e, "DROPPED")) {
        log.debug(
            "Skipping already-cleaned execution sponsor nonce slot: txId={}, reason={}",
            transaction.transactionId(),
            e.getMessage());
        return;
      }
      throw e;
    }
  }

  private static boolean isSlotNotFound(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("nonce slot not found");
  }

  private static boolean isStaleActual(Web3TransactionStateInvalidException e, String actual) {
    return e.getMessage() != null
        && e.getMessage().contains("stale nonce slot transition")
        && e.getMessage().contains("actual=" + actual);
  }
}
