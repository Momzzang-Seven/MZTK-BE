package momzzangseven.mztkbe.modules.level.infrastructure.repository;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XpLedgerJpaRepository extends JpaRepository<XpLedgerEntity, Long> {

  boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn);

  Slice<XpLedgerEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
