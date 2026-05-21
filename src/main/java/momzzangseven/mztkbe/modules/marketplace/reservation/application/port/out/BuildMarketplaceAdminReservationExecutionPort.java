package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareMarketplaceAdminEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationAdminExecutionDraft;

public interface BuildMarketplaceAdminReservationExecutionPort {

  ReservationAdminExecutionDraft buildRefund(PrepareMarketplaceAdminEscrowCommand command);

  ReservationAdminExecutionDraft buildSettlement(PrepareMarketplaceAdminEscrowCommand command);
}
