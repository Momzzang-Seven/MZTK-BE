package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent.EscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event.EscrowDispatchEventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for a trainer to reject a PENDING reservation.
 *
 * <p><b>Transaction ordering (DB-first, escrow-after-commit):</b><br>
 * The reservation row is saved with a sentinel {@code txHash} value ( {@value
 * EscrowDispatchEventListener#PENDING_TX_HASH}), and the DB transaction is committed. After the
 * commit, two AFTER_COMMIT events are dispatched:
 *
 * <ol>
 *   <li>{@link EscrowDispatchEvent} — calls {@code cancelClass} on-chain and writes back the real
 *       txHash via {@code EscrowDispatchEventListener}.
 *   <li>{@link TrainerStrikeEvent} — records a trainer strike via {@code
 *       ReservationSanctionEventListener}.
 * </ol>
 *
 * <p>This ordering guarantees the DB row is durable before any on-chain side-effect is triggered,
 * preventing the "on-chain success + DB rollback" divergence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RejectReservationService implements RejectReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public RejectReservationResult execute(RejectReservationCommand command) {
    log.debug(
        "RejectReservation: reservationId={}, trainerId={}",
        command.reservationId(),
        command.authenticatedTrainerId());

    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + command.reservationId()));

    if (!reservation.isOwnedByTrainer(command.authenticatedTrainerId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }

    if (!reservation.getStatus().canTransitionTo(ReservationStatus.REJECTED)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot reject reservation in status: " + reservation.getStatus());
    }

    // Persist REJECTED status with a sentinel txHash; the real escrow call happens AFTER_COMMIT.
    Reservation rejected =
        reservation.reject(EscrowDispatchEventListener.PENDING_TX_HASH, command.rejectionReason());
    Reservation saved = saveReservationPort.save(rejected);

    // Publish events — both handled AFTER_COMMIT by their respective listeners.
    eventPublisher.publishEvent(
        EscrowDispatchEvent.of(saved.getId(), reservation.getOrderId(), EscrowAction.CANCEL));
    eventPublisher.publishEvent(
        new TrainerStrikeEvent(reservation.getTrainerId(), TrainerStrikeEvent.REASON_REJECT));

    log.info(
        "Reservation rejected: id={}, trainerId={}",
        saved.getId(),
        command.authenticatedTrainerId());
    return new RejectReservationResult(saved.getId(), saved.getStatus());
  }
}
