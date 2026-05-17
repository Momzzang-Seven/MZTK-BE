package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-item transaction processor for the auto-cancel batch job.
 *
 * <p>Each reservation is processed in its own {@code REQUIRES_NEW} transaction. This isolates
 * failures: if on-chain refund or strike recording fails for one reservation, the others in the
 * same scheduler run are not rolled back.
 *
 * <p>This class must be a separate Spring bean (not a private inner class) because Spring AOP
 * proxy-based transaction management only intercepts calls that cross bean boundaries.
 *
 * <p><b>Transaction ordering (DB-first, escrow-after):</b><br>
 * The reservation status is persisted first. Then the on-chain {@code adminRefund} call is made. If
 * the escrow call fails, the status remains TIMEOUT_CANCELLED in DB (correct terminal state) and
 * the strike is still recorded. The missing on-chain refund can be resolved by a reconciliation
 * job. This prevents the inverse failure (on-chain success + DB rollback) which would be harder to
 * detect and recover from.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCancelBatchItemProcessor {

  private static final String PENDING_TX_HASH = "ESCROW_DISPATCH_PENDING";

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final RecordTrainerStrikePort recordTrainerStrikePort;
  private final Clock clock;
  private TransactionOperations postCommitTransactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.postCommitTransactionOperations = transactionTemplate;
  }

  /**
   * Processes a single auto-cancel item in its own isolated transaction.
   *
   * <p>Re-fetches the reservation with a pessimistic write lock at the start of the REQUIRES_NEW
   * transaction to guard against stale-read race conditions. Without this, a concurrent
   * USER_CANCELLED transaction committed between the batch read and this process() call would not
   * be visible — the stale PENDING status would bypass the guard and trigger a duplicate on-chain
   * refund (double-compensation).
   *
   * <p>Order: re-fetch with lock → validate status → persist TIMEOUT_CANCELLED + strike → commit →
   * submit adminRefund on-chain → update txHash in a new short transaction. This prevents
   * successful on-chain refund from being rolled back locally by a commit failure.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(Reservation staleReservation) {
    // Re-fetch with pessimistic write lock to prevent concurrent state conflicts.
    // A USER_CANCELLED committed between the batch read and here would be invisible
    // in the stale object, causing a duplicate on-chain refund.
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(staleReservation.getId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "AutoCancel skipped: reservation {} no longer exists",
                      staleReservation.getId());
                  return new IllegalStateException(
                      "Reservation not found: " + staleReservation.getId());
                });

    // Guard: re-validate status with the fresh locked row before any side-effect.
    LocalDateTime now = LocalDateTime.now(clock);
    if (!reservation.getStatus().canTimeoutCancel()
        || !reservation.isLegacySchedulerEligibleAt(now)) {
      log.info(
          "AutoCancel skipped: reservation {} is not legacy scheduler eligible (status={}, flow={})",
          reservation.getId(),
          reservation.getStatus(),
          reservation.getEffectiveEscrowFlow());
      return;
    }

    // 1. Persist status first — DB is the source of truth.
    Reservation cancelled = reservation.timeoutCancel(PENDING_TX_HASH);
    saveReservationPort.save(cancelled);

    recordTrainerStrikePort.recordStrike(
        reservation.getTrainerId(), TrainerStrikeEvent.REASON_TIMEOUT);

    runAfterCommit(
        () ->
            submitAdminRefundAndWriteBack(
                reservation.getId(), reservation.getOrderId(), reservation.getTrainerId()));

    log.info(
        "AutoCancel committed local timeout state: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }

  private void submitAdminRefundAndWriteBack(Long reservationId, String orderId, Long trainerId) {
    String refundTxHash;
    try {
      refundTxHash = submitEscrowTransactionPort.submitAdminRefund(orderId);
    } catch (RuntimeException e) {
      log.error(
          "AutoCancel adminRefund failed after local commit: reservationId={}, trainerId={}",
          reservationId,
          trainerId,
          e);
      return;
    }

    runPostCommitTransaction(
        () ->
            loadReservationPort
                .findByIdWithLock(reservationId)
                .ifPresentOrElse(
                    current -> writeBackRefundTxHash(current, refundTxHash),
                    () ->
                        log.warn(
                            "AutoCancel txHash write-back skipped: reservation {} no longer exists",
                            reservationId)));
  }

  private void writeBackRefundTxHash(Reservation current, String refundTxHash) {
    if (current.getStatus() != ReservationStatus.TIMEOUT_CANCELLED
        || !PENDING_TX_HASH.equals(current.getTxHash())) {
      log.info(
          "AutoCancel txHash write-back skipped: reservationId={}, status={}, txHash={}",
          current.getId(),
          current.getStatus(),
          current.getTxHash());
      return;
    }
    saveReservationPort.save(current.updateTxHash(refundTxHash));
    log.info(
        "AutoCancel on-chain refund recorded: reservationId={}, txHash={}",
        current.getId(),
        refundTxHash);
  }

  private void runAfterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      runAfterCommitSafely(action);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            runAfterCommitSafely(action);
          }
        });
  }

  private void runAfterCommitSafely(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.error("AutoCancel after-commit callback failed", e);
    }
  }

  private void runPostCommitTransaction(Runnable action) {
    if (postCommitTransactionOperations == null) {
      action.run();
      return;
    }
    postCommitTransactionOperations.executeWithoutResult(status -> action.run());
  }
}
