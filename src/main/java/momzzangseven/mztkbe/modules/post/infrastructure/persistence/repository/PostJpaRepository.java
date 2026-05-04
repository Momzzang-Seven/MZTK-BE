package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
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

  // Used by admin/manual reconciliation to update only the publication status while guarding
  // against stale reads.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PostEntity p set p.publicationStatus = :targetStatus"
          + " where p.id = :postId"
          + " and p.type = :postType"
          + " and p.publicationStatus = :currentStatus")
  int updatePublicationStatusByIdIfCurrent(
      @Param("postId") Long postId,
      @Param("postType") PostType postType,
      @Param("currentStatus") PostPublicationStatus currentStatus,
      @Param("targetStatus") PostPublicationStatus targetStatus);

  // Used by reconciliation when publication lifecycle metadata must be adjusted explicitly,
  // without merging a full post aggregate.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PostEntity p"
          + " set p.publicationStatus = :targetStatus,"
          + " p.currentCreateExecutionIntentId = :currentCreateExecutionIntentId,"
          + " p.publicationFailureTerminalStatus = :publicationFailureTerminalStatus,"
          + " p.publicationFailureReason = :publicationFailureReason"
          + " where p.id = :postId"
          + " and p.type = :postType"
          + " and p.publicationStatus = :currentStatus")
  int updatePublicationStateByIdIfCurrent(
      @Param("postId") Long postId,
      @Param("postType") PostType postType,
      @Param("currentStatus") PostPublicationStatus currentStatus,
      @Param("targetStatus") PostPublicationStatus targetStatus,
      @Param("currentCreateExecutionIntentId") String currentCreateExecutionIntentId,
      @Param("publicationFailureTerminalStatus") String publicationFailureTerminalStatus,
      @Param("publicationFailureReason") String publicationFailureReason);

  // Used by recovery flows that must claim one exact failed publication row before applying
  // local follow-up edits. Every nullable lifecycle metadata field is compared null-safely.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PostEntity p"
          + " set p.publicationStatus = :targetStatus,"
          + " p.currentCreateExecutionIntentId = :currentCreateExecutionIntentId,"
          + " p.publicationFailureTerminalStatus = :publicationFailureTerminalStatus,"
          + " p.publicationFailureReason = :publicationFailureReason"
          + " where p.id = :postId"
          + " and p.type = :postType"
          + " and p.publicationStatus = :expectedStatus"
          + " and ((:expectedCurrentCreateExecutionIntentId is null"
          + " and p.currentCreateExecutionIntentId is null)"
          + " or p.currentCreateExecutionIntentId = :expectedCurrentCreateExecutionIntentId)"
          + " and ((:expectedPublicationFailureTerminalStatus is null"
          + " and p.publicationFailureTerminalStatus is null)"
          + " or p.publicationFailureTerminalStatus = :expectedPublicationFailureTerminalStatus)"
          + " and ((:expectedPublicationFailureReason is null"
          + " and p.publicationFailureReason is null)"
          + " or p.publicationFailureReason = :expectedPublicationFailureReason)")
  int updatePublicationStateByIdIfExpected(
      @Param("postId") Long postId,
      @Param("postType") PostType postType,
      @Param("expectedStatus") PostPublicationStatus expectedStatus,
      @Param("expectedCurrentCreateExecutionIntentId")
          String expectedCurrentCreateExecutionIntentId,
      @Param("expectedPublicationFailureTerminalStatus")
          String expectedPublicationFailureTerminalStatus,
      @Param("expectedPublicationFailureReason") String expectedPublicationFailureReason,
      @Param("targetStatus") PostPublicationStatus targetStatus,
      @Param("currentCreateExecutionIntentId") String currentCreateExecutionIntentId,
      @Param("publicationFailureTerminalStatus") String publicationFailureTerminalStatus,
      @Param("publicationFailureReason") String publicationFailureReason);

  // Keep both v2 tag cursor native queries aligned with
  // PostPersistenceAdapter#containsCursorSearch: when search is present,
  // exclude FREE posts and match QUESTION titles only.
  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE (:type IS NULL OR p.type = :type)
            AND p.publication_status = 'VISIBLE'
            AND p.moderation_status = 'NORMAL'
            AND (
              :search IS NULL
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

  // Keep the same v2 search policy as the first-page tag cursor query.
  @Query(
      value =
          """
          SELECT p.*
          FROM posts p
          WHERE (:type IS NULL OR p.type = :type)
            AND p.publication_status = 'VISIBLE'
            AND p.moderation_status = 'NORMAL'
            AND (
              :search IS NULL
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
}
