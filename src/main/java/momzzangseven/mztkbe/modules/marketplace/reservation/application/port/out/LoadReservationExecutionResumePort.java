package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;

public interface LoadReservationExecutionResumePort {

  Optional<ReservationExecutionResumeView> loadLatest(Long reservationId);

  Map<Long, ReservationExecutionResumeView> loadLatestBatch(Collection<Long> reservationIds);
}
