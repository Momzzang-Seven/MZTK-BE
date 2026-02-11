package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

  // 1. 게시글의 최상위 댓글(루트) 조회 (parent가 NULL인 것들)
  @Query("SELECT c FROM CommentEntity c WHERE c.postId = :postId AND c.parent IS NULL")
  Page<CommentEntity> findRootCommentsByPostId(@Param("postId") Long postId, Pageable pageable);

  // 2. 특정 댓글의 대댓글 조회 (parent가 특정 ID인 것들)
  @Query("SELECT c FROM CommentEntity c WHERE c.parent.id = :parentId")
  Page<CommentEntity> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);
}
