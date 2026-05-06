package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a list of reservations assigned to the authenticated trainer.
 *
 * <p>Results are ordered by {@code reservationDate} descending (most recent first). An optional
 * status filter can be applied to narrow the results.
 *
 * <p>Enriches each summary with class title, thumbnail, and trainer nickname via batch cross-module
 * lookups to avoid N+1 calls.
 */
@Service
@RequiredArgsConstructor
public class GetTrainerReservationsService implements GetTrainerReservationsUseCase {

  private final LoadReservationPort loadReservationPort;
  private final LoadClassSummaryPort loadClassSummaryPort;
  private final LoadUserSummaryPort loadUserSummaryPort;

  @Override
  @Transactional(readOnly = true)
  public List<ReservationSummaryResult> execute(GetTrainerReservationsQuery query) {
    query.validate();
    List<Reservation> reservations =
        loadReservationPort.findByTrainerId(query.trainerId(), query.status());

    List<Long> slotIds = reservations.stream().map(Reservation::getSlotId).toList();
    List<Long> trainerIds = reservations.stream().map(Reservation::getTrainerId).toList();

    Map<Long, ClassSummary> classSummaries = loadClassSummaryPort.findBySlotIds(slotIds);
    Map<Long, UserSummary> trainerSummaries = loadUserSummaryPort.findByIds(trainerIds);

    return reservations.stream()
        .map(
            r ->
                ReservationSummaryResult.from(
                    r,
                    classSummaries.get(r.getSlotId()),
                    trainerSummaries.get(r.getTrainerId())))
        .toList();
  }
}
