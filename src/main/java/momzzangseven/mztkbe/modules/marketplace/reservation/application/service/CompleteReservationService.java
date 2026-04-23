package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationEarlyCompleteException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
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
 * Service that marks a reservation as SETTLED after the user confirms class completion.
 *
 * <p>Guards:
 *
 * <ol>
 *   <li>Ownership — only the reservation's buyer may confirm.
 *   <li>Status transition — must be in APPROVED state.
 *   <li>Early-complete prevention — session end time must have passed (uses injected {@link Clock}
 *       for testable, timezone-aware "now").
 * </ol>
 *
 * <p><b>Transaction ordering (DB-first, escrow-after-commit):</b><br>
 * The reservation row is saved as SETTLED with a sentinel {@code txHash}, the DB transaction is
 * committed, then {@link EscrowDispatchEvent} is handled AFTER_COMMIT by
 * {@code EscrowDispatchEventListener} to call {@code confirmClass} on-chain and write back the real
 * txHash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteReservationService implements CompleteReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Injected clock for testable, timezone-aware "now" computation.
   *
   * <p>In production this is bound to {@code Asia/Seoul} by the {@code @Bean Clock} in {@code
   * TimeConfig}. In tests, a fixed clock can be substituted via the constructor.
   */
  private final Clock clock;

  @Override
  @Transactional
  public CompleteReservationResult execute(CompleteReservationCommand command) {
    log.debug(
        "CompleteReservation: reservationId={}, userId={}",
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

    if (!reservation.getStatus().canTransitionTo(ReservationStatus.SETTLED)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot complete reservation in status: " + reservation.getStatus());
    }

    LocalDateTime sessionEnd =
        LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime())
            .plusMinutes(reservation.getDurationMinutes());
    if (LocalDateTime.now(clock).isBefore(sessionEnd)) {
      throw new ReservationEarlyCompleteException();
    }

    // Persist SETTLED with sentinel txHash; real escrow confirmClass call happens AFTER_COMMIT.
    Reservation completed = reservation.complete(EscrowDispatchEventListener.PENDING_TX_HASH);
    Reservation saved = saveReservationPort.save(completed);

    eventPublisher.publishEvent(
        new EscrowDispatchEvent(saved.getId(), reservation.getOrderId(), EscrowAction.CONFIRM));

    log.info("Reservation settled: id={}, userId={}", saved.getId(), command.userId());
    return new CompleteReservationResult(saved.getId(), saved.getStatus());
  }
}
