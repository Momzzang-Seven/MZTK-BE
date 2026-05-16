package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;

public interface SaveReservationCreateIdempotencyPort {

  ReservationCreateIdempotency save(ReservationCreateIdempotency idempotency);
}
