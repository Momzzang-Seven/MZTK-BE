package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerSanctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainerSanctionJpaRepository extends JpaRepository<TrainerSanctionEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM TrainerSanctionEntity t WHERE t.trainerId = :trainerId")
  Optional<TrainerSanctionEntity> findByIdWithLock(@Param("trainerId") Long trainerId);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO trainer_sanctions (trainer_id, strike_count, created_at, updated_at)
          VALUES (:trainerId, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          ON CONFLICT (trainer_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(@Param("trainerId") Long trainerId);
}
