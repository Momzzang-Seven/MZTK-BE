package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

  long countByPostId(Long postId);

  Page<CommentEntity> findByPostId(Long postId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM CommentEntity c WHERE c.id = :id")
  Optional<CommentEntity> findByIdForUpdate(@Param("id") Long id);

  @Query(
      "SELECT c.writerId AS userId, COUNT(c.id) AS commentCount "
          + "FROM CommentEntity c "
          + "WHERE c.writerId IN :userIds AND c.isDeleted = false "
          + "GROUP BY c.writerId")
  List<UserCommentCount> countCommentsByUserIds(@Param("userIds") List<Long> userIds);

  @Query(
      "SELECT c.postId AS postId, COUNT(c.id) AS commentCount "
          + "FROM CommentEntity c "
          + "WHERE c.postId IN :postIds AND c.isDeleted = false "
          + "GROUP BY c.postId")
  List<PostCommentCount> countCommentsByPostIds(@Param("postIds") List<Long> postIds);

  // 1. 최상위 댓글 조회
  @Query(
      "SELECT c FROM CommentEntity c WHERE c.postId = :postId AND c.parent IS NULL ORDER BY c.createdAt ASC, c.id ASC")
  Page<CommentEntity> findRootCommentsByPostId(@Param("postId") Long postId, Pageable pageable);

  @Query(
      "SELECT c FROM CommentEntity c "
          + "WHERE c.postId = :postId AND c.parent IS NULL "
          + "ORDER BY c.createdAt ASC, c.id ASC")
  List<CommentEntity> findRootCommentsByPostIdFirstPage(
      @Param("postId") Long postId, Pageable pageable);

  @Query(
      "SELECT c FROM CommentEntity c "
          + "WHERE c.postId = :postId AND c.parent IS NULL "
          + "AND (c.createdAt > :cursorCreatedAt "
          + "OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId)) "
          + "ORDER BY c.createdAt ASC, c.id ASC")
  List<CommentEntity> findRootCommentsByPostIdAfterCursor(
      @Param("postId") Long postId,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  // 2. 대댓글 조회
  @Query(
      "SELECT c FROM CommentEntity c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC, c.id ASC")
  Page<CommentEntity> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

  @Query(
      "SELECT c FROM CommentEntity c "
          + "WHERE c.parent.id = :parentId "
          + "ORDER BY c.createdAt ASC, c.id ASC")
  List<CommentEntity> findRepliesByParentIdFirstPage(
      @Param("parentId") Long parentId, Pageable pageable);

  @Query(
      "SELECT c FROM CommentEntity c "
          + "WHERE c.parent.id = :parentId "
          + "AND (c.createdAt > :cursorCreatedAt "
          + "OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId)) "
          + "ORDER BY c.createdAt ASC, c.id ASC")
  List<CommentEntity> findRepliesByParentIdAfterCursor(
      @Param("parentId") Long parentId,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  @Query(
      "SELECT c.parent.id AS parentId, COUNT(c.id) AS replyCount "
          + "FROM CommentEntity c "
          + "WHERE c.parent.id IN :parentIds "
          + "GROUP BY c.parent.id")
  List<DirectReplyCount> countDirectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

  @Query(
      value =
          """
          SELECT ranked.post_id AS "postId",
                 ranked.latest_comment_id AS "latestCommentId",
                 ranked.latest_commented_at AS "latestCommentedAt"
          FROM (
            SELECT c.post_id,
                   c.id AS latest_comment_id,
                   c.created_at AS latest_commented_at,
                   ROW_NUMBER() OVER (
                     PARTITION BY c.post_id
                     ORDER BY c.created_at DESC, c.id DESC
                   ) AS rn
            FROM comments c
            JOIN posts p ON p.id = c.post_id
            WHERE c.writer_id = :userId
              AND c.is_deleted = false
              AND p.type = :postType
              AND (
                (p.publication_status = 'VISIBLE' AND p.moderation_status = 'NORMAL')
                OR p.user_id = :userId
              )
              AND (:search IS NULL OR LOWER(p.title) LIKE CONCAT('%', :search, '%') ESCAPE '!')
          ) ranked
          WHERE ranked.rn = 1
          ORDER BY ranked.latest_commented_at DESC, ranked.latest_comment_id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<CommentedPostRefProjection> findCommentedPostRefsFirstPage(
      @Param("userId") Long userId,
      @Param("postType") String postType,
      @Param("search") String search,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT ranked.post_id AS "postId",
                 ranked.latest_comment_id AS "latestCommentId",
                 ranked.latest_commented_at AS "latestCommentedAt"
          FROM (
            SELECT c.post_id,
                   c.id AS latest_comment_id,
                   c.created_at AS latest_commented_at,
                   ROW_NUMBER() OVER (
                     PARTITION BY c.post_id
                     ORDER BY c.created_at DESC, c.id DESC
                   ) AS rn
            FROM comments c
            JOIN posts p ON p.id = c.post_id
            WHERE c.writer_id = :userId
              AND c.is_deleted = false
              AND p.type = :postType
              AND (
                (p.publication_status = 'VISIBLE' AND p.moderation_status = 'NORMAL')
                OR p.user_id = :userId
              )
              AND (:search IS NULL OR LOWER(p.title) LIKE CONCAT('%', :search, '%') ESCAPE '!')
          ) ranked
          WHERE ranked.rn = 1
            AND (
              ranked.latest_commented_at < :cursorCreatedAt
              OR (
                ranked.latest_commented_at = :cursorCreatedAt
                AND ranked.latest_comment_id < :cursorId
              )
            )
          ORDER BY ranked.latest_commented_at DESC, ranked.latest_comment_id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<CommentedPostRefProjection> findCommentedPostRefsAfterCursor(
      @Param("userId") Long userId,
      @Param("postType") String postType,
      @Param("search") String search,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE comments SET is_deleted = true, updated_at = CURRENT_TIMESTAMP "
              + "WHERE post_id = :postId",
      nativeQuery = true)
  void deleteAllByPostId(@Param("postId") Long postId);

  @Query("SELECT c.id FROM CommentEntity c WHERE c.isDeleted = true AND c.updatedAt < :cutoff")
  List<Long> findIdsByIsDeletedTrueAndUpdatedAtBefore(
      @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM CommentEntity c WHERE c.parent.id IN :parentIds")
  void deleteByParentIdIn(@Param("parentIds") List<Long> parentIds);

  interface DirectReplyCount {
    Long getParentId();

    Long getReplyCount();
  }

  interface PostCommentCount {
    Long getPostId();

    Long getCommentCount();
  }

  interface UserCommentCount {
    Long getUserId();

    Long getCommentCount();
  }

  interface CommentedPostRefProjection {
    Long getPostId();

    Long getLatestCommentId();

    LocalDateTime getLatestCommentedAt();
  }
}
