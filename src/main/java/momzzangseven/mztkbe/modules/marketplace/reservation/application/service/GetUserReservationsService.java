package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a list of reservations belonging to the authenticated user.
 *
 * <p>Results are ordered by {@code reservationDate} descending (most recent first). An optional
 * status filter can be applied to narrow the results.
 */
@Service
@RequiredArgsConstructor
public class GetUserReservationsService implements GetUserReservationsUseCase {

  private final LoadReservationPort loadReservationPort;

  @Override
  @Transactional(readOnly = true)
  public List<ReservationSummaryResult> execute(GetUserReservationsQuery query) {
    return loadReservationPort.findByUserId(query.userId(), query.status()).stream()
        .map(ReservationSummaryResult::from)
        .toList();
  }
}
