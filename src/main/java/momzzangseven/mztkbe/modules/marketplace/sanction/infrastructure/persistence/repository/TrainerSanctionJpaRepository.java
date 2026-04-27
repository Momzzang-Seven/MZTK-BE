package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerSanctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainerSanctionJpaRepository extends JpaRepository<TrainerSanctionEntity, Long> {}
