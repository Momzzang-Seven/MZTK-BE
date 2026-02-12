package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

  // 1. 최상위 댓글 조회
  @Query(
      "SELECT c FROM CommentEntity c WHERE c.postId = :postId AND c.parent IS NULL ORDER BY c.createdAt ASC, c.id ASC")
  Page<CommentEntity> findRootCommentsByPostId(@Param("postId") Long postId, Pageable pageable);

  // 2. 대댓글 조회
  @Query(
      "SELECT c FROM CommentEntity c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC, c.id ASC")
  Page<CommentEntity> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE CommentEntity c SET c.isDeleted = true, c.updatedAt = CURRENT_TIMESTAMP WHERE c.postId = :postId")
  void deleteAllByPostId(@Param("postId") Long postId);
}
