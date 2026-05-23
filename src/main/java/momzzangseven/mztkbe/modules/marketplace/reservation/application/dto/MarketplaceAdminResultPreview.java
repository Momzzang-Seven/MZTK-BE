package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;

public record MarketplaceAdminResultPreview(
    ReservationStatus targetReservationStatus,
    ReservationEscrowStatus targetEscrowStatus,
    ReservationTerminalResolvedBy resolvedBy,
    String terminalReasonCode) {}
