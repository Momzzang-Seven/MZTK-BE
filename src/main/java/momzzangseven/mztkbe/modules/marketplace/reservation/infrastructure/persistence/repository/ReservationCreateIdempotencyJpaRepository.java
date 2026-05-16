package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationCreateIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationCreateIdempotencyJpaRepository
    extends JpaRepository<ReservationCreateIdempotencyEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT k FROM ReservationCreateIdempotencyEntity k "
          + "WHERE k.buyerId = :buyerId AND k.keyHash = :keyHash")
  Optional<ReservationCreateIdempotencyEntity> findByBuyerIdAndKeyHashWithLock(
      @Param("buyerId") Long buyerId, @Param("keyHash") String keyHash);

  @Query(
      "SELECT k.currentExecutionIntentPublicId FROM ReservationCreateIdempotencyEntity k "
          + "WHERE k.currentExecutionIntentPublicId IN :publicIds")
  List<String> findCurrentExecutionIntentPublicIdsIn(
      @Param("publicIds") Collection<String> publicIds);
}
