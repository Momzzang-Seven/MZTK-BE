package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.TrainerStrikeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for a trainer to reject a PENDING reservation.
 *
 * <p>Triggers on-chain cancelClass to refund the user. Publishes a {@link TrainerStrikeEvent}
 * (AFTER_COMMIT) for the sanction listener to record a strike asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RejectReservationService implements RejectReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
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
            .findById(command.reservationId())
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

    String cancelTxHash = submitEscrowTransactionPort.submitCancel(reservation.getOrderId());
    Reservation rejected = reservation.reject(cancelTxHash);
    Reservation saved = saveReservationPort.save(rejected);

    // Publish strike event — handled AFTER_COMMIT by ReservationSanctionEventListener
    eventPublisher.publishEvent(
        new TrainerStrikeEvent(reservation.getTrainerId(), RecordTrainerStrikeCommand.REASON_REJECT));

    log.info(
        "Reservation rejected: id={}, trainerId={}",
        saved.getId(),
        command.authenticatedTrainerId());
    return new RejectReservationResult(saved.getId(), saved.getStatus());
  }
}
