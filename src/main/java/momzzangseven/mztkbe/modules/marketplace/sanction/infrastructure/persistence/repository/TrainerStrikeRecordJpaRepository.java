package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerStrikeRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainerStrikeRecordJpaRepository extends JpaRepository<TrainerStrikeRecordEntity, Long> {
}
