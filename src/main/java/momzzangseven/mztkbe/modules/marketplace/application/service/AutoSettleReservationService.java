package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.AutoSettleReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch service that auto-settles APPROVED reservations when the user fails to confirm within 24 h
 * after class end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSettleReservationService implements AutoSettleReservationUseCase {

  private static final int BATCH_SIZE = 50;

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;

  @Override
  @Transactional
  public int runBatch(LocalDateTime now) {
    List<Reservation> candidates = loadReservationPort.findApprovedForAutoSettle(now, BATCH_SIZE);

    if (candidates.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (Reservation reservation : candidates) {
      try {
        String settleTxHash =
            submitEscrowTransactionPort.submitAdminSettle(reservation.getOrderId());
        Reservation settled = reservation.autoSettle(settleTxHash);
        saveReservationPort.save(settled);
        processed++;
        log.info(
            "AutoSettle: reservationId={}, trainerId={}",
            reservation.getId(),
            reservation.getTrainerId());
      } catch (Exception e) {
        log.error(
            "AutoSettle failed for reservationId={}: {}", reservation.getId(), e.getMessage(), e);
      }
    }
    return processed;
  }
}
