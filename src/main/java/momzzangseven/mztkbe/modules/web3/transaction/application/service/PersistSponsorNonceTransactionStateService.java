package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
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
        if (isSlotNotFound(e) || isStaleActual(e, SponsorNonceSlotStatus.STUCK)) {
          log.warn(
              "Skipping nonce slot stuck transition for txId={}: {}",
              command.transactionId(),
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
}
