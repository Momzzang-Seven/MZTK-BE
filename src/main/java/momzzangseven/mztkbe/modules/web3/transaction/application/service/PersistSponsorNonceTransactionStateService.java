package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.MarkExecutionIntentPendingOnchainPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistSponsorNonceTransactionStateService
    implements PersistSponsorNonceTransactionStateUseCase {

  private final UpdateTransactionPort updateTransactionPort;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final MarkExecutionIntentPendingOnchainPort markExecutionIntentPendingOnchainPort;

  @Override
  @Transactional
  public void markSigned(SponsorNonceSignedCommand command) {
    updateTransactionPort.markSigned(
        command.transactionId(), command.nonce(), command.signedRawTx(), command.txHash());
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(command.nonce())
            .fromStatus(SponsorNonceSlotStatus.RESERVED)
            .toStatus(SponsorNonceSlotStatus.SIGNED)
            .activeAttemptId(command.attemptId())
            .activeTxId(command.transactionId())
            .stateChangedAt(command.stateChangedAt())
            .hasRawTx(true)
            .hasTxHash(command.txHash() != null && !command.txHash().isBlank())
            .hasSigningEvidence(true)
            .build());
  }

  @Override
  @Transactional
  public void markPending(SponsorNoncePendingCommand command) {
    updateTransactionPort.markPending(command.transactionId(), command.txHash());
    markNonceSlotBroadcasted(command);
    markExecutionIntentPendingOnchainPort.markPendingOnchain(command.transactionId());
  }

  @Override
  @Transactional
  public void markPendingWithoutSlotTransition(TransactionPendingCommand command) {
    updateTransactionPort.markPending(command.transactionId(), command.txHash());
    markExecutionIntentPendingOnchainPort.markPendingOnchain(command.transactionId());
  }

  @Override
  @Transactional
  public void markUnconfirmed(SponsorNonceUnconfirmedCommand command) {
    markNonceSlotStuck(command);
    updateTransactionPort.updateStatus(
        command.transactionId(),
        Web3TxStatus.UNCONFIRMED,
        command.txHash(),
        command.failureReason());
  }

  @Override
  @Transactional
  public void failTerminalAndDropReservedSlot(
      SponsorNonceTerminalReservedSlotFailureCommand command) {
    updateTransactionPort.scheduleRetry(command.transactionId(), command.failureReason(), null);
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(command.chainId())
              .fromAddress(command.fromAddress())
              .nonce(command.nonce())
              .fromStatus(SponsorNonceSlotStatus.RESERVED)
              .toStatus(SponsorNonceSlotStatus.DROPPED)
              .activeAttemptId(command.attemptId())
              .activeTxId(command.transactionId())
              .releasedAttemptId(command.attemptId())
              .releasedTxId(command.transactionId())
              .stateChangedAt(command.stateChangedAt())
              .releaseReason(command.failureReason())
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isStaleActual(e, SponsorNonceSlotStatus.DROPPED)) {
        log.debug(
            "Skipping already-dropped reserved nonce slot for terminal tx failure: txId={}, nonce={}",
            command.transactionId(),
            command.nonce());
        return;
      }
      throw e;
    }
  }

  @Override
  @Transactional
  public void markSignedOperatorReview(SponsorNonceSignedOperatorReviewCommand command) {
    boolean shouldMarkTransaction =
        transitionSlotToOperatorReview(
            command.chainId(),
            command.fromAddress(),
            command.nonce(),
            SponsorNonceSlotStatus.SIGNED,
            null,
            command.transactionId(),
            command.stateChangedAt(),
            command.slotTerminalReason(),
            false,
            false,
            false,
            false);
    if (shouldMarkTransaction) {
      updateTransactionPort.markUnconfirmedForSponsorNonceReview(
          command.transactionId(), command.transactionFailureReason());
    }
  }

  @Override
  @Transactional
  public void markBroadcastingOperatorReview(
      SponsorNonceBroadcastingOperatorReviewCommand command) {
    boolean shouldMarkTransaction =
        transitionSlotToOperatorReview(
            command.chainId(),
            command.fromAddress(),
            command.nonce(),
            SponsorNonceSlotStatus.BROADCASTING,
            command.attemptId(),
            command.transactionId(),
            command.stateChangedAt(),
            command.slotTerminalReason(),
            command.hasRawTx(),
            command.hasTxHash(),
            command.hasSigningEvidence(),
            command.hasBroadcastEvidence());
    if (shouldMarkTransaction) {
      updateTransactionPort.markUnconfirmedForSponsorNonceReview(
          command.transactionId(), command.transactionFailureReason());
    }
  }

  private boolean transitionSlotToOperatorReview(
      long chainId,
      String fromAddress,
      Long nonce,
      SponsorNonceSlotStatus fromStatus,
      Long activeAttemptId,
      Long activeTxId,
      LocalDateTime stateChangedAt,
      String terminalReason,
      boolean hasRawTx,
      boolean hasTxHash,
      boolean hasSigningEvidence,
      boolean hasBroadcastEvidence) {
    if (nonce == null) {
      return true;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(chainId)
              .fromAddress(fromAddress)
              .nonce(nonce)
              .fromStatus(fromStatus)
              .toStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
              .activeAttemptId(activeAttemptId)
              .activeTxId(activeTxId)
              .stateChangedAt(stateChangedAt)
              .terminalReason(terminalReason)
              .hasRawTx(hasRawTx)
              .hasTxHash(hasTxHash)
              .hasSigningEvidence(hasSigningEvidence)
              .hasBroadcastEvidence(hasBroadcastEvidence)
              .build());
      return true;
    } catch (Web3TransactionStateInvalidException e) {
      if (isStaleActual(e, SponsorNonceSlotStatus.CONSUMED)) {
        log.debug(
            "Skipping transaction operator-review downgrade because nonce slot is consumed: txId={}, nonce={}",
            activeTxId,
            nonce);
        return false;
      }
      if (isSlotNotFound(e)
          || isStaleActual(e, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED_UNKNOWN)
          || isStaleActual(e, SponsorNonceSlotStatus.STUCK)) {
        log.debug(
            "Skipping nonce slot operator review transition for txId={}, nonce={}: {}",
            activeTxId,
            nonce,
            e.getMessage());
        return true;
      }
      throw e;
    }
  }

  private void markNonceSlotStuck(SponsorNonceUnconfirmedCommand command) {
    if (command.nonce() == null) {
      return;
    }
    Web3TransactionStateInvalidException lastStaleException = null;
    for (SponsorNonceSlotStatus fromStatus :
        List.of(SponsorNonceSlotStatus.BROADCASTED, SponsorNonceSlotStatus.BROADCASTING)) {
      try {
        nonceSlotLifecycleUseCase.transition(
            RecordSponsorNonceSlotTransitionCommand.builder()
                .chainId(command.chainId())
                .fromAddress(command.fromAddress())
                .nonce(command.nonce())
                .fromStatus(fromStatus)
                .toStatus(SponsorNonceSlotStatus.STUCK)
                .activeTxId(command.transactionId())
                .stateChangedAt(command.stateChangedAt())
                .stuckReason(command.failureReason())
                .hasTxHash(command.txHash() != null && !command.txHash().isBlank())
                .hasSigningEvidence(true)
                .hasBroadcastEvidence(true)
                .build());
        return;
      } catch (Web3TransactionStateInvalidException e) {
        if (isSlotNotFound(e)) {
          log.warn(
              "Skipping nonce slot stuck transition for txId={}: {}",
              command.transactionId(),
              e.getMessage());
          return;
        }
        if (isStaleActual(e, SponsorNonceSlotStatus.STUCK)) {
          if (isSlotStuckByTransaction(command)) {
            log.warn(
                "Skipping already-stuck nonce slot transition for txId={}: {}",
                command.transactionId(),
                e.getMessage());
            return;
          }
          throw e;
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

  private void markNonceSlotBroadcasted(SponsorNoncePendingCommand command) {
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(command.chainId())
              .fromAddress(command.fromAddress())
              .nonce(command.nonce())
              .fromStatus(SponsorNonceSlotStatus.BROADCASTING)
              .toStatus(SponsorNonceSlotStatus.BROADCASTED)
              .activeAttemptId(command.attemptId())
              .activeTxId(command.transactionId())
              .stateChangedAt(command.stateChangedAt())
              .hasRawTx(true)
              .hasTxHash(command.txHash() != null && !command.txHash().isBlank())
              .hasSigningEvidence(true)
              .hasBroadcastEvidence(true)
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isStaleActual(e, SponsorNonceSlotStatus.BROADCASTED)) {
        if (isSlotBroadcastedByTransaction(command)) {
          log.warn(
              "Skipping already-broadcasted nonce slot transition for txId={}: {}",
              command.transactionId(),
              e.getMessage());
          return;
        }
        throw e;
      }
      throw e;
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

  private boolean isSlotStuckByTransaction(SponsorNonceUnconfirmedCommand command) {
    if (command.fromAddress() == null || command.fromAddress().isBlank()) {
      return false;
    }
    return nonceSlotLifecycleUseCase
        .loadSlotForReview(command.chainId(), command.fromAddress(), command.nonce())
        .filter(slot -> isStuckSlotForTransaction(slot, command.transactionId()))
        .isPresent();
  }

  private boolean isSlotBroadcastedByTransaction(SponsorNoncePendingCommand command) {
    if (command.fromAddress() == null || command.fromAddress().isBlank()) {
      return false;
    }
    return nonceSlotLifecycleUseCase
        .loadSlotForReview(command.chainId(), command.fromAddress(), command.nonce())
        .filter(
            slot ->
                isBroadcastedSlotForTransaction(slot, command.transactionId(), command.attemptId()))
        .isPresent();
  }

  private boolean isStuckSlotForTransaction(SponsorNonceSlotView slot, Long transactionId) {
    return slot.status() == SponsorNonceSlotStatus.STUCK
        && transactionId != null
        && transactionId.equals(slot.activeTxId());
  }

  private boolean isBroadcastedSlotForTransaction(
      SponsorNonceSlotView slot, Long transactionId, Long attemptId) {
    return slot.status() == SponsorNonceSlotStatus.BROADCASTED
        && transactionId != null
        && transactionId.equals(slot.activeTxId())
        && (attemptId == null || attemptId.equals(slot.activeAttemptId()));
  }
}
