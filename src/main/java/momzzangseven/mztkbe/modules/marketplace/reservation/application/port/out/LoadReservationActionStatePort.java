package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;

public interface LoadReservationActionStatePort {

  Optional<MarketplaceReservationActionState> findById(Long actionStateId);

  Optional<MarketplaceReservationActionState> findByIdWithLock(Long actionStateId);

  Optional<MarketplaceReservationActionState> findLatestByReservationId(Long reservationId);

  Optional<MarketplaceReservationActionState> findLatestByReservationIdWithLock(Long reservationId);

  Optional<MarketplaceReservationActionState> findByExecutionIntentPublicIdWithLock(
      String executionIntentPublicId);

  List<MarketplaceReservationActionState> findByReservationIdAndStatuses(
      Long reservationId, List<ReservationActionStateStatus> statuses);

  Optional<MarketplaceReservationActionState> findLatestByReservationIdAndActionType(
      Long reservationId, ReservationEscrowAction actionType);

  Optional<MarketplaceReservationActionState> findLatestByReservationIdAndActionTypeWithLock(
      Long reservationId, ReservationEscrowAction actionType);

  List<MarketplaceReservationActionState> findExpiredAdminPreparingAttemptsWithLock(
      LocalDateTime now, int batchSize);

  List<MarketplaceReservationActionState> findBoundAdminExecutionAttemptsForTerminalReplay(
      int batchSize);
}
