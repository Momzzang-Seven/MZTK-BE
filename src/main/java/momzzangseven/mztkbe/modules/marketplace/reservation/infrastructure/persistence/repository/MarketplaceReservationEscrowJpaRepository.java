package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationEscrowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceReservationEscrowJpaRepository
    extends JpaRepository<MarketplaceReservationEscrowEntity, Long> {

  Optional<MarketplaceReservationEscrowEntity> findByReservationId(Long reservationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT e FROM MarketplaceReservationEscrowEntity e WHERE e.reservationId = :reservationId")
  Optional<MarketplaceReservationEscrowEntity> findByReservationIdWithLock(
      @Param("reservationId") Long reservationId);

  Optional<MarketplaceReservationEscrowEntity> findByOrderKey(String orderKey);
}
