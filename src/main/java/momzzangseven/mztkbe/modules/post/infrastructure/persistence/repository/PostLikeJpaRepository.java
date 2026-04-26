package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeJpaRepository extends JpaRepository<PostLikeEntity, Long> {

  boolean existsByTargetTypeAndTargetIdAndUserId(
      PostLikeTargetType targetType, Long targetId, Long userId);

  Optional<PostLikeEntity> findByTargetTypeAndTargetIdAndUserId(
      PostLikeTargetType targetType, Long targetId, Long userId);

  @Modifying
  @Query(
      value =
          "INSERT INTO post_like (target_type, target_id, user_id, created_at) "
              + "VALUES (:targetType, :targetId, :userId, CURRENT_TIMESTAMP) "
              + "ON CONFLICT (target_type, target_id, user_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("targetType") String targetType,
      @Param("targetId") Long targetId,
      @Param("userId") Long userId);

  @Modifying
  @Query(
      "delete from PostLikeEntity p "
          + "where p.targetType = :targetType and p.targetId = :targetId and p.userId = :userId")
  void deleteByTargetTypeAndTargetIdAndUserId(
      @Param("targetType") PostLikeTargetType targetType,
      @Param("targetId") Long targetId,
      @Param("userId") Long userId);

  long countByTargetTypeAndTargetId(PostLikeTargetType targetType, Long targetId);

  @Query(
      "select p.targetId as targetId, count(p) as likeCount "
          + "from PostLikeEntity p "
          + "where p.targetType = :targetType and p.targetId in :targetIds "
          + "group by p.targetId")
  List<TargetLikeCountProjection> countByTargetIds(
      @Param("targetType") PostLikeTargetType targetType,
      @Param("targetIds") Collection<Long> targetIds);

  @Query(
      "select p.targetId "
          + "from PostLikeEntity p "
          + "where p.targetType = :targetType and p.targetId in :targetIds and p.userId = :userId")
  List<Long> findLikedTargetIds(
      @Param("targetType") PostLikeTargetType targetType,
      @Param("targetIds") Collection<Long> targetIds,
      @Param("userId") Long userId);

  @Modifying
  @Query("delete from PostLikeEntity p where p.targetType = :targetType and p.targetId = :targetId")
  void deleteByTargetTypeAndTargetId(
      @Param("targetType") PostLikeTargetType targetType, @Param("targetId") Long targetId);

  @Query(
      value =
          """
          SELECT
              pl.id AS likeId,
              pl.created_at AS likedAt,
              p.id AS postId,
              p.user_id AS userId,
              p.type AS type,
              p.title AS title,
              p.content AS content,
              p.reward AS reward,
              p.accepted_answer_id AS acceptedAnswerId,
              p.status AS status,
              p.created_at AS postCreatedAt,
              p.updated_at AS postUpdatedAt
          FROM post_like pl
          JOIN posts p ON p.id = pl.target_id
          WHERE pl.user_id = :userId
            AND pl.target_type = 'POST'
            AND p.type = :type
          ORDER BY pl.created_at DESC, pl.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<LikedPostProjection> findLikedPostsFirstPageNative(
      @Param("userId") Long userId, @Param("type") String type, @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT
              pl.id AS likeId,
              pl.created_at AS likedAt,
              p.id AS postId,
              p.user_id AS userId,
              p.type AS type,
              p.title AS title,
              p.content AS content,
              p.reward AS reward,
              p.accepted_answer_id AS acceptedAnswerId,
              p.status AS status,
              p.created_at AS postCreatedAt,
              p.updated_at AS postUpdatedAt
          FROM post_like pl
          JOIN posts p ON p.id = pl.target_id
          WHERE pl.user_id = :userId
            AND pl.target_type = 'POST'
            AND p.type = :type
            AND (
              pl.created_at < :cursorLikedAt
              OR (pl.created_at = :cursorLikedAt AND pl.id < :cursorLikeId)
            )
          ORDER BY pl.created_at DESC, pl.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<LikedPostProjection> findLikedPostsAfterCursorNative(
      @Param("userId") Long userId,
      @Param("type") String type,
      @Param("cursorLikedAt") LocalDateTime cursorLikedAt,
      @Param("cursorLikeId") Long cursorLikeId,
      @Param("limit") int limit);

  interface TargetLikeCountProjection {
    Long getTargetId();

    Long getLikeCount();
  }

  interface LikedPostProjection {
    Long getLikeId();

    LocalDateTime getLikedAt();

    Long getPostId();

    Long getUserId();

    String getType();

    String getTitle();

    String getContent();

    Long getReward();

    Long getAcceptedAnswerId();

    String getStatus();

    LocalDateTime getPostCreatedAt();

    LocalDateTime getPostUpdatedAt();
  }
}
