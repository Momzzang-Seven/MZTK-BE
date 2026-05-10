package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically compensates a freshly reserved nonce when the row will not broadcast.
 *
 * <p>Order matters inside the single {@link Transactional} boundary:
 *
 * <ol>
 *   <li>Clear the row's {@code nonce} so the partial unique index {@code uk_web3_tx_sender_nonce}
 *       no longer covers it — a sibling reserver that races between writes cannot collide.
 *   <li>Stamp the terminal failure reason and clear the processing lock so the row cannot be
 *       re-claimed even if the cursor rollback below throws.
 *   <li>CAS-roll back the {@code web3_nonce_state.next_nonce} cursor; if another reservation has
 *       already advanced past {@code nonce + 1} the gap is unrecoverable here and we surface it for
 *       ops triage.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservedNonceCompensator {

  private final UpdateTransactionPort updateTransactionPort;
  private final ReserveNoncePort reserveNoncePort;

  @Transactional
  public void compensate(
      Long transactionId,
      String fromAddress,
      long reservedNonce,
      Web3TxFailureReason terminalReason) {
    validate(transactionId, fromAddress, reservedNonce, terminalReason);

    updateTransactionPort.clearNonce(transactionId);
    updateTransactionPort.scheduleRetry(transactionId, terminalReason.code(), null);
    boolean released = reserveNoncePort.releaseNonce(fromAddress, reservedNonce);
    if (!released) {
      // TODO(F-1): replace this log line with a `web3_nonce_gap_incidents` recorder so each gap
      // leaves a permanent, queryable record reusable across transaction / execution / eip7702
      // modules. See PR #150 review thread.
      // TODO(F-2): consider serializing reserve→sign→broadcast/compensate per fromAddress via
      // pg_try_advisory_xact_lock to remove the multi-worker race window that produces this gap
      // in the first place. See PR #150 review thread.
      log.error(
          "NONCE_GAP_DETECTED: txId={}, fromAddress={}, abandonedNonce={} "
              + "— another reservation advanced the cursor before release",
          transactionId,
          fromAddress,
          reservedNonce);
    }
  }

  private static void validate(
      Long transactionId,
      String fromAddress,
      long reservedNonce,
      Web3TxFailureReason terminalReason) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new Web3InvalidInputException("fromAddress is required");
    }
    if (reservedNonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (terminalReason == null) {
      throw new Web3InvalidInputException("terminalReason is required");
    }
    if (terminalReason.isRetryable()) {
      throw new Web3InvalidInputException(
          "terminalReason must be non-retryable: " + terminalReason.code());
    }
  }
}
