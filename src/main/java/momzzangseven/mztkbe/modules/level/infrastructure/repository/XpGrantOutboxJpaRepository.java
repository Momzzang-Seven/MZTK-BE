package momzzangseven.mztkbe.modules.level.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.vo.XpGrantOutboxStatus;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpGrantOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpGrantOutboxJpaRepository extends JpaRepository<XpGrantOutboxEntity, Long> {

  @Query(
      "SELECT o FROM XpGrantOutboxEntity o "
          + "WHERE o.status = :status AND o.nextAttemptAt <= :now "
          + "ORDER BY o.nextAttemptAt ASC")
  List<XpGrantOutboxEntity> findDueBatch(
      @Param("status") XpGrantOutboxStatus status,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  @Query(
      value =
          "SELECT * FROM xp_grant_outbox WHERE id = :id AND status = 'PENDING' "
              + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<XpGrantOutboxEntity> findByIdForUpdateSkipLocked(@Param("id") Long id);
}
