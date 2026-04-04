package momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.ExternalDisconnectTaskEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link ExternalDisconnectTaskEntity}. */
public interface ExternalDisconnectTaskJpaRepository
    extends JpaRepository<ExternalDisconnectTaskEntity, Long> {

  @Query(
      "select t from ExternalDisconnectTaskEntity t "
          + "where t.status = :status and t.nextAttemptAt <= :now "
          + "order by t.nextAttemptAt asc, t.id asc")
  List<ExternalDisconnectTaskEntity> findDueTasks(
      @Param("status") ExternalDisconnectStatus status,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  @Modifying
  @Query(
      "delete from ExternalDisconnectTaskEntity t "
          + "where t.status = :status and t.updatedAt < :cutoff")
  int deleteByStatusAndUpdatedAtBefore(
      @Param("status") ExternalDisconnectStatus status, @Param("cutoff") LocalDateTime cutoff);

  @Modifying
  @Query("delete from ExternalDisconnectTaskEntity t where t.userId in :userIds")
  int deleteByUserIdIn(@Param("userIds") List<Long> userIds);
}
