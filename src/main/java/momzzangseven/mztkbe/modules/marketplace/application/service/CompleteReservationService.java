package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationEarlyCompleteException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteReservationService implements CompleteReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;

  @Override
  @Transactional
  public CompleteReservationResult execute(CompleteReservationCommand command) {
    log.debug(
        "CompleteReservation: reservationId={}, userId={}",
        command.reservationId(),
        command.userId());

    Reservation reservation =
        loadReservationPort
            .findById(command.reservationId())
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

    LocalDateTime sessionStart =
        LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime());
    if (LocalDateTime.now().isBefore(sessionStart)) {
      throw new ReservationEarlyCompleteException();
    }

    String confirmTxHash = submitEscrowTransactionPort.submitConfirm(reservation.getOrderId());
    Reservation completed = reservation.complete(confirmTxHash);
    Reservation saved = saveReservationPort.save(completed);

    log.info("Reservation settled: id={}, userId={}", saved.getId(), command.userId());
    return new CompleteReservationResult(saved.getId(), saved.getStatus());
  }
}
