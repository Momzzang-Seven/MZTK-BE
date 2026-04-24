package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

  long countByPostId(Long postId);

  @Query(
      "SELECT c.postId AS postId, COUNT(c.id) AS commentCount "
          + "FROM CommentEntity c "
          + "WHERE c.postId IN :postIds "
          + "GROUP BY c.postId")
  List<PostCommentCount> countCommentsByPostIds(@Param("postIds") List<Long> postIds);

  // 1. 최상위 댓글 조회
  @Query(
      "SELECT c FROM CommentEntity c WHERE c.postId = :postId AND c.parent IS NULL ORDER BY c.createdAt ASC, c.id ASC")
  Page<CommentEntity> findRootCommentsByPostId(@Param("postId") Long postId, Pageable pageable);

  // 2. 대댓글 조회
  @Query(
      "SELECT c FROM CommentEntity c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC, c.id ASC")
  Page<CommentEntity> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

  @Query(
      "SELECT c.parent.id AS parentId, COUNT(c.id) AS replyCount "
          + "FROM CommentEntity c "
          + "WHERE c.parent.id IN :parentIds "
          + "GROUP BY c.parent.id")
  List<DirectReplyCount> countDirectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

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
}
