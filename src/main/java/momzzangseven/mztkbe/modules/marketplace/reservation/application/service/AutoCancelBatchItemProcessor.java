package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;

/**
 * Per-item transaction processor for the auto-cancel batch job.
 *
 * <p>Each reservation is processed in its own {@code REQUIRES_NEW} transaction. This isolates
 * failures: if on-chain refund or strike recording fails for one reservation, the others in the
 * same scheduler run are not rolled back.
 *
 * <p>Transaction boundaries are delegated to {@link RunReservationTransactionPort} so this
 * application service does not depend on Spring transaction infrastructure.
 *
 * <p><b>Transaction ordering (DB-first, side-effects-after):</b><br>
 * The reservation status is persisted first. Trainer strike recording and the on-chain {@code
 * adminRefund} call run after commit. If either side effect fails, the status remains
 * TIMEOUT_CANCELLED in DB (correct terminal state). Missing side effects can be resolved by a
 * reconciliation job. This prevents the inverse failure (side effect success + DB rollback) which
 * would be harder to detect and recover from.
 */
@Slf4j
@RequiredArgsConstructor
public class AutoCancelBatchItemProcessor {

  private static final String PENDING_TX_HASH = "ESCROW_DISPATCH_PENDING";

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final RecordTrainerStrikePort recordTrainerStrikePort;
  private final RunReservationTransactionPort runReservationTransactionPort;
  private final RunReservationPostCommitPort runReservationPostCommitPort;
  private final Clock clock;

  /**
   * Processes a single auto-cancel item in its own isolated transaction.
   *
   * <p>Re-fetches the reservation with a pessimistic write lock at the start of the REQUIRES_NEW
   * transaction to guard against stale-read race conditions. Without this, a concurrent
   * USER_CANCELLED transaction committed between the batch read and this process() call would not
   * be visible — the stale PENDING status would bypass the guard and trigger a duplicate on-chain
   * refund (double-compensation).
   *
   * <p>Order: re-fetch with lock → validate status → persist TIMEOUT_CANCELLED → commit → record
   * idempotent timeout strike → submit adminRefund on-chain → update txHash in a new short
   * transaction. This prevents successful side effects from being rolled back locally by a commit
   * failure.
   */
  public void process(Reservation staleReservation) {
    runReservationTransactionPort.requiresNew(
        () -> {
          processInTransaction(staleReservation);
          return null;
        });
  }

  private void processInTransaction(Reservation staleReservation) {
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

    runReservationPostCommitPort.afterCommit(
        "AutoCancel",
        () ->
            recordTimeoutStrikeAndSubmitAdminRefund(
                reservation.getId(), reservation.getOrderId(), reservation.getTrainerId()));

    log.info(
        "AutoCancel committed local timeout state: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }

  private void recordTimeoutStrikeAndSubmitAdminRefund(
      Long reservationId, String orderId, Long trainerId) {
    recordTimeoutStrike(reservationId, trainerId);
    submitAdminRefundAndWriteBack(reservationId, orderId, trainerId);
  }

  private void recordTimeoutStrike(Long reservationId, Long trainerId) {
    try {
      recordTrainerStrikePort.recordStrike(
          trainerId,
          TrainerStrikeEvent.REASON_TIMEOUT,
          RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_TIMEOUT,
          String.valueOf(reservationId));
    } catch (RuntimeException e) {
      log.error(
          "AutoCancel timeout strike failed after local commit: reservationId={}, trainerId={}",
          reservationId,
          trainerId,
          e);
    }
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

    runReservationPostCommitPort.requiresNew(
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
}
