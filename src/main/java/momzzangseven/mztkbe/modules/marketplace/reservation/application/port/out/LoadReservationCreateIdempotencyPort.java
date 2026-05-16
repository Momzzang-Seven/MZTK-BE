package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;

public interface LoadReservationCreateIdempotencyPort {

  Optional<ReservationCreateIdempotency> findByBuyerIdAndKeyHashWithLock(
      Long buyerId, String keyHash);
}
