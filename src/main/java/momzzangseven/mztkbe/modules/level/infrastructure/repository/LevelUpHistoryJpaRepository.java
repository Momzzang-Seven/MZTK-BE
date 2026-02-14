package momzzangseven.mztkbe.modules.level.infrastructure.repository;

import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelUpHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelUpHistoryJpaRepository extends JpaRepository<LevelUpHistoryEntity, Long> {
  Slice<LevelUpHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
