package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationSlotDateLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationSlotDateLockJpaRepository
    extends JpaRepository<ReservationSlotDateLockEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT l FROM ReservationSlotDateLockEntity l "
          + "WHERE l.slotId = :slotId AND l.reservationDate = :reservationDate")
  Optional<ReservationSlotDateLockEntity> findBySlotIdAndReservationDateForUpdate(
      @Param("slotId") Long slotId, @Param("reservationDate") LocalDate reservationDate);
}
