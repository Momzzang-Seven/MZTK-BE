package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerSanctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainerSanctionJpaRepository extends JpaRepository<TrainerSanctionEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM TrainerSanctionEntity t WHERE t.trainerId = :trainerId")
  Optional<TrainerSanctionEntity> findByIdWithLock(@Param("trainerId") Long trainerId);
}
