package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

/**
 * Repairs reservation read models from marketplace escrow chain state when a read detects a gap.
 */
public interface RepairReservationChainReadUseCase {

  Reservation repairOne(Reservation reservation);

  List<Reservation> repairBatch(List<Reservation> reservations);
}
