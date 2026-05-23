package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationAdminExecutionDraft;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.SubmitMarketplaceAdminEscrowResult;

public interface SubmitMarketplaceAdminReservationExecutionPort {

  SubmitMarketplaceAdminEscrowResult submit(ReservationAdminExecutionDraft draft);
}
