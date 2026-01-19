package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelRetentionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LevelRetentionPersistenceAdapter implements LevelRetentionPort {

  private final EntityManager entityManager;

  @Override
  @Transactional
  public void deleteUserLevelDataByUserIds(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }

    entityManager
        .createNativeQuery("delete from xp_ledger where user_id in (:userIds)")
        .setParameter("userIds", userIds)
        .executeUpdate();
    entityManager
        .createNativeQuery("delete from level_up_histories where user_id in (:userIds)")
        .setParameter("userIds", userIds)
        .executeUpdate();
    entityManager
        .createNativeQuery("delete from user_progress where user_id in (:userIds)")
        .setParameter("userIds", userIds)
        .executeUpdate();
  }

  @Override
  @Transactional
  public int deleteXpLedgerBefore(LocalDateTime cutoff, int batchSize) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff is required");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be > 0");
    }
    return entityManager
        .createNativeQuery(
            """
            delete from xp_ledger
            where id in (
              select id
              from xp_ledger
              where created_at < :cutoff
              order by created_at asc
              limit :batchSize
            )
            """)
        .setParameter("cutoff", cutoff)
        .setParameter("batchSize", batchSize)
        .executeUpdate();
  }

  @Override
  @Transactional
  public int deleteLevelUpHistoriesBefore(LocalDateTime cutoff, int batchSize) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff is required");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be > 0");
    }
    return entityManager
        .createNativeQuery(
            """
            delete from level_up_histories
            where id in (
              select id
              from level_up_histories
              where created_at < :cutoff
              order by created_at asc
              limit :batchSize
            )
            """)
        .setParameter("cutoff", cutoff)
        .setParameter("batchSize", batchSize)
        .executeUpdate();
  }
}
