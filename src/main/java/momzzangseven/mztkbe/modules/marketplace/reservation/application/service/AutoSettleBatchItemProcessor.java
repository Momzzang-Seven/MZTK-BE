package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Per-item transaction processor for the auto-settle batch job.
 *
 * <p>Each reservation is settled in its own {@code REQUIRES_NEW} transaction so that a single
 * on-chain or persistence failure does not prevent other candidates from being settled in the same
 * scheduler run.
 *
 * <p>Transaction boundaries are delegated to {@link RunReservationTransactionPort} so this
 * application service does not depend on Spring transaction infrastructure.
 *
 * <p><b>Transaction ordering (DB-first, escrow-after):</b><br>
 * The reservation status is persisted as AUTO_SETTLED first. Then the on-chain {@code adminSettle}
 * call is made and the txHash is written back. If the escrow call fails, the DB status is still
 * AUTO_SETTLED (correct terminal state) and the missing txHash can be reconciled separately.
 */
@Slf4j
@RequiredArgsConstructor
public class AutoSettleBatchItemProcessor {

  private static final String PENDING_TX_HASH = "ESCROW_DISPATCH_PENDING";

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final RunReservationTransactionPort runReservationTransactionPort;
  private final RunReservationPostCommitPort runReservationPostCommitPort;
  private final Clock clock;

  /**
   * Settles a single approved reservation in its own isolated transaction.
   *
   * <p>Re-fetches the reservation with a pessimistic write lock at the start of the REQUIRES_NEW
   * transaction to guard against stale-read race conditions. Without this, a concurrent state
   * change (e.g. user cancellation) committed between the batch read and this process() call would
   * not be visible, potentially triggering a duplicate on-chain settle.
   *
   * <p>Order: re-fetch with lock → validate status → persist AUTO_SETTLED → commit → submit
   * adminSettle on-chain → update txHash in a new short transaction. This prevents successful
   * on-chain settlement from being rolled back locally by a commit failure.
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
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(staleReservation.getId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "AutoSettle skipped: reservation {} no longer exists",
                      staleReservation.getId());
                  return new IllegalStateException(
                      "Reservation not found: " + staleReservation.getId());
                });

    // Guard: re-validate status with the fresh locked row before any side-effect.
    LocalDateTime now = LocalDateTime.now(clock);
    if (!reservation.getStatus().canTransitionTo(ReservationStatus.AUTO_SETTLED)
        || !reservation.isLegacySchedulerEligibleAt(now)) {
      log.info(
          "AutoSettle skipped: reservation {} is not legacy scheduler eligible (status={}, flow={})",
          reservation.getId(),
          reservation.getStatus(),
          reservation.getEffectiveEscrowFlow());
      return;
    }

    // 1. Persist status first.
    Reservation settled = reservation.autoSettle(PENDING_TX_HASH);
    saveReservationPort.save(settled);

    runReservationPostCommitPort.afterCommit(
        "AutoSettle",
        () -> submitAdminSettleAndWriteBack(reservation.getId(), reservation.getOrderId()));

    log.info(
        "AutoSettle committed local settle state: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }

  private void submitAdminSettleAndWriteBack(Long reservationId, String orderId) {
    String settleTxHash;
    try {
      settleTxHash = submitEscrowTransactionPort.submitAdminSettle(orderId);
    } catch (RuntimeException e) {
      log.error(
          "AutoSettle adminSettle failed after local commit: reservationId={}", reservationId, e);
      return;
    }

    runReservationPostCommitPort.requiresNew(
        () ->
            loadReservationPort
                .findByIdWithLock(reservationId)
                .ifPresentOrElse(
                    current -> writeBackSettleTxHash(current, settleTxHash),
                    () ->
                        log.warn(
                            "AutoSettle txHash write-back skipped: reservation {} no longer exists",
                            reservationId)));
  }

  private void writeBackSettleTxHash(Reservation current, String settleTxHash) {
    if (current.getStatus() != ReservationStatus.AUTO_SETTLED
        || !PENDING_TX_HASH.equals(current.getTxHash())) {
      log.info(
          "AutoSettle txHash write-back skipped: reservationId={}, status={}, txHash={}",
          current.getId(),
          current.getStatus(),
          current.getTxHash());
      return;
    }
    saveReservationPort.save(current.updateTxHash(settleTxHash));
    log.info(
        "AutoSettle on-chain settle recorded: reservationId={}, txHash={}",
        current.getId(),
        settleTxHash);
  }
}
