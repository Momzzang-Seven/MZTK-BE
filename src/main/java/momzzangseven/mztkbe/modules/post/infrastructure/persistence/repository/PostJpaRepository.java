package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostJpaRepository extends JpaRepository<PostEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM PostEntity p WHERE p.id = :id")
  Optional<PostEntity> findByIdForUpdate(@Param("id") Long id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PostEntity p set p.status = :resolvedStatus"
          + " where p.id = :postId and p.type = :postType and p.status = :openStatus")
  int markResolvedByIdIfType(
      @Param("postId") Long postId,
      @Param("postType") PostType postType,
      @Param("openStatus") PostStatus openStatus,
      @Param("resolvedStatus") PostStatus resolvedStatus);

  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE (:type IS NULL OR p.type = :type)
            AND (
              :search IS NULL
              OR p.type = 'FREE'
              OR (
                p.type = 'QUESTION'
                AND LOWER(p.title) LIKE CONCAT('%', :search, '%') ESCAPE '!'
              )
            )
            AND EXISTS (
              SELECT 1
              FROM post_tags pt
              WHERE pt.post_id = p.id
                AND pt.tag_id = :tagId
            )
          ORDER BY p.created_at DESC, p.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PostEntity> findPostsByConditionWithTagFirstPageNative(
      @Param("type") String type,
      @Param("search") String search,
      @Param("tagId") Long tagId,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE (:type IS NULL OR p.type = :type)
            AND (
              :search IS NULL
              OR p.type = 'FREE'
              OR (
                p.type = 'QUESTION'
                AND LOWER(p.title) LIKE CONCAT('%', :search, '%') ESCAPE '!'
              )
            )
            AND EXISTS (
              SELECT 1
              FROM post_tags pt
              WHERE pt.post_id = p.id
                AND pt.tag_id = :tagId
            )
            AND (
              p.created_at < :cursorCreatedAt
              OR (p.created_at = :cursorCreatedAt AND p.id < :cursorId)
            )
          ORDER BY p.created_at DESC, p.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PostEntity> findPostsByConditionWithTagAfterCursorNative(
      @Param("type") String type,
      @Param("search") String search,
      @Param("tagId") Long tagId,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE p.user_id = :authorId
            AND p.type = :type
          ORDER BY p.created_at DESC, p.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PostEntity> findPostsByAuthorFirstPageNative(
      @Param("authorId") Long authorId, @Param("type") String type, @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE p.user_id = :authorId
            AND p.type = :type
            AND (
              p.created_at < :cursorCreatedAt
              OR (p.created_at = :cursorCreatedAt AND p.id < :cursorId)
            )
          ORDER BY p.created_at DESC, p.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PostEntity> findPostsByAuthorAfterCursorNative(
      @Param("authorId") Long authorId,
      @Param("type") String type,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE p.user_id = :authorId
            AND p.type = :type
            AND EXISTS (
              SELECT 1
              FROM post_tags pt
              WHERE pt.post_id = p.id
                AND pt.tag_id = :tagId
            )
          ORDER BY p.created_at DESC, p.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PostEntity> findPostsByAuthorWithTagFirstPageNative(
      @Param("authorId") Long authorId,
      @Param("type") String type,
      @Param("tagId") Long tagId,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE p.user_id = :authorId
            AND p.type = :type
            AND EXISTS (
              SELECT 1
              FROM post_tags pt
              WHERE pt.post_id = p.id
                AND pt.tag_id = :tagId
            )
            AND (
              p.created_at < :cursorCreatedAt
              OR (p.created_at = :cursorCreatedAt AND p.id < :cursorId)
            )
          ORDER BY p.created_at DESC, p.id DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PostEntity> findPostsByAuthorWithTagAfterCursorNative(
      @Param("authorId") Long authorId,
      @Param("type") String type,
      @Param("tagId") Long tagId,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  @Query(
      "SELECT p.userId AS userId, COUNT(p.id) AS postCount "
          + "FROM PostEntity p "
          + "WHERE p.userId IN :userIds "
          + "GROUP BY p.userId")
  List<UserPostCount> countPostsByUserIds(@Param("userIds") List<Long> userIds);

  interface UserPostCount {
    Long getUserId();

    Long getPostCount();
  }
}
