package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;

/**
 * Input port for marketplace reservation Web3 recovery.
 *
 * <p>Recovery intentionally accepts local purchase rows that are still displayed as purchase
 * pending ({@code HOLDING + PURCHASE_PENDING}) so a user can replay/repair an already-confirmed
 * execution intent when the asynchronous post-confirm hook failed after commit.
 */
public interface RecoverReservationEscrowUseCase {

  RecoverReservationEscrowResult execute(RecoverReservationEscrowCommand command);
}
