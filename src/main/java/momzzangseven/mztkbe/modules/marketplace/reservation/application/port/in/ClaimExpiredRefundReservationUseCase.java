package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationResult;

/** Input port for the buyer-triggered marketplace deadline refund flow. */
public interface ClaimExpiredRefundReservationUseCase {

  ClaimExpiredRefundReservationResult execute(ClaimExpiredRefundReservationCommand command);
}
