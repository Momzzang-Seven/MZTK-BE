package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;

public interface SaveReservationCreateIdempotencyPort {

  ReservationCreateIdempotency save(ReservationCreateIdempotency idempotency);

  ReserveCreateIdempotencyResult reservePreparing(
      Long buyerId, String keyHash, String payloadHash, LocalDateTime expiresAt);

  Optional<ReservationCreateIdempotency> replaceActionStateIfCurrent(
      Long idempotencyId, Long expectedActionStateId, Long newActionStateId);

  record ReserveCreateIdempotencyResult(
      ReservationCreateIdempotency idempotency, boolean created) {}
}
