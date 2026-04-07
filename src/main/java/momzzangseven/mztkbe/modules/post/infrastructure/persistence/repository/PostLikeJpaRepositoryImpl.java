package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
import org.springframework.stereotype.Repository;

@Repository
public class PostLikeJpaRepositoryImpl implements PostLikeJpaRepositoryCustom {

  private static final String INSERT_IF_ABSENT_RETURNING_SQL =
      """
      WITH inserted AS (
          INSERT INTO post_like (target_type, target_id, user_id, created_at)
          VALUES (:targetType, :targetId, :userId, CURRENT_TIMESTAMP)
          ON CONFLICT (target_type, target_id, user_id) DO NOTHING
          RETURNING id, target_type, target_id, user_id, created_at
      )
      SELECT id, target_type, target_id, user_id, created_at
      FROM inserted
      UNION ALL
      SELECT pl.id, pl.target_type, pl.target_id, pl.user_id, pl.created_at
      FROM post_like pl
      WHERE pl.target_type = :targetType
        AND pl.target_id = :targetId
        AND pl.user_id = :userId
        AND NOT EXISTS (SELECT 1 FROM inserted)
      LIMIT 1
      """;

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Optional<PostLikeEntity> insertIfAbsentReturning(
      String targetType, Long targetId, Long userId) {
    @SuppressWarnings("unchecked")
    List<PostLikeEntity> results =
        entityManager
            .createNativeQuery(INSERT_IF_ABSENT_RETURNING_SQL, PostLikeEntity.class)
            .setParameter("targetType", targetType)
            .setParameter("targetId", targetId)
            .setParameter("userId", userId)
            .getResultList();

    return results.stream().findFirst();
  }
}
