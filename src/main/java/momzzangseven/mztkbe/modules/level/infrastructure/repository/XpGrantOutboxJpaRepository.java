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

  // PostgreSQL-only (FOR UPDATE SKIP LOCKED). Exercise this path only in e2eTest against real
  // Postgres; H2 unit/integration tests must mock XpGrantOutboxPort instead of hitting this query.
  // Rechecks next_attempt_at so a stale due-batch cannot re-claim a row whose backoff has been
  // pushed into the future by an earlier failed attempt.
  @Query(
      value =
          "SELECT * FROM xp_grant_outbox WHERE id = :id AND status = 'PENDING' "
              + "AND next_attempt_at <= :now FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<XpGrantOutboxEntity> findByIdForUpdateSkipLocked(
      @Param("id") Long id, @Param("now") LocalDateTime now);

  // PostgreSQL-only (FOR UPDATE, blocking — NOT SKIP LOCKED). Used by recordFailure to re-lock the
  // row and observe any terminal state another worker committed; returns empty once the row is no
  // longer PENDING so a late failure can never overwrite a DONE/FAILED terminal state.
  @Query(
      value = "SELECT * FROM xp_grant_outbox WHERE id = :id AND status = 'PENDING' FOR UPDATE",
      nativeQuery = true)
  Optional<XpGrantOutboxEntity> findPendingByIdForUpdate(@Param("id") Long id);
}
