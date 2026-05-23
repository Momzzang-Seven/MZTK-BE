package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCandidateView;

public interface LoadReservationExecutionCandidatePort {

  List<ReservationExecutionCandidateView> findByReservationResource(Long reservationId);

  default List<ReservationExecutionCandidateView> findByReservationResource(
      Long reservationId, String orderKey) {
    return findByReservationResource(reservationId);
  }
}
