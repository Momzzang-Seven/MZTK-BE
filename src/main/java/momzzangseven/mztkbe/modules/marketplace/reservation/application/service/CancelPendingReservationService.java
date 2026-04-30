package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent.EscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event.EscrowDispatchEventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for a user to cancel their own PENDING reservation.
 *
 * <p>Only PENDING reservations may be cancelled by the user. Once a trainer approves (APPROVED),
 * the user can no longer cancel unilaterally.
 *
 * <p><b>Transaction ordering (DB-first, escrow-after-commit):</b><br>
 * The reservation row is saved as USER_CANCELLED with a sentinel {@code txHash}, the DB transaction
 * is committed, then {@link EscrowDispatchEvent} is handled AFTER_COMMIT by {@code
 * EscrowDispatchEventListener} to call {@code cancelClass} on-chain and write back the real txHash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelPendingReservationService implements CancelPendingReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public CancelPendingReservationResult execute(CancelPendingReservationCommand command) {
    log.debug(
        "CancelPendingReservation: reservationId={}, userId={}",
        command.reservationId(),
        command.userId());

    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + command.reservationId()));

    if (!reservation.isOwnedByUser(command.userId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }

    if (!reservation.getStatus().canTransitionTo(ReservationStatus.USER_CANCELLED)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot cancel reservation in status: " + reservation.getStatus());
    }

    // Persist USER_CANCELLED with sentinel txHash; real escrow cancelClass call happens
    // AFTER_COMMIT.
    Reservation cancelled = reservation.cancelByUser(EscrowDispatchEventListener.PENDING_TX_HASH);
    Reservation saved = saveReservationPort.save(cancelled);

    eventPublisher.publishEvent(
        EscrowDispatchEvent.of(saved.getId(), reservation.getOrderId(), EscrowAction.CANCEL));

    log.info("Reservation user-cancelled: id={}, userId={}", saved.getId(), command.userId());
    return new CancelPendingReservationResult(saved.getId(), saved.getStatus());
  }
}
