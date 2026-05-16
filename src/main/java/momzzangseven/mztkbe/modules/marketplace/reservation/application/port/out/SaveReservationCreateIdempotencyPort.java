package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;

public interface SaveReservationCreateIdempotencyPort {

  ReservationCreateIdempotency save(ReservationCreateIdempotency idempotency);

  ReserveCreateIdempotencyResult reservePreparing(
      Long buyerId, String keyHash, String payloadHash, LocalDateTime expiresAt);

  record ReserveCreateIdempotencyResult(
      ReservationCreateIdempotency idempotency, boolean created) {}
}
