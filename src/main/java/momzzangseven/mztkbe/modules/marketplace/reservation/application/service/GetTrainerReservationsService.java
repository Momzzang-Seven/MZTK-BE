package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a list of reservations assigned to the authenticated trainer.
 *
 * <p>Results are ordered by {@code reservationDate} descending (most recent first). An optional
 * status filter can be applied to narrow the results.
 */
@Service
@RequiredArgsConstructor
public class GetTrainerReservationsService implements GetTrainerReservationsUseCase {

  private final LoadReservationPort loadReservationPort;

  @Override
  @Transactional(readOnly = true)
  public List<ReservationSummaryResult> execute(GetTrainerReservationsQuery query) {
    query.validate();
    return loadReservationPort.findByTrainerId(query.trainerId(), query.status()).stream()
        .map(ReservationSummaryResult::from)
        .toList();
  }
}
