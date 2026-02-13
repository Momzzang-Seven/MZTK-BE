package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentPersistenceAdapter
    implements LoadCommentPort, SaveCommentPort, DeleteCommentPort {

  private final CommentJpaRepository commentRepository;

  // ========== SaveCommentPort Implementation ==========

  @Override
  @Transactional
  public Comment saveComment(Comment comment) {
    CommentEntity parentEntity = null;

    if (comment.getParentId() != null) {
      parentEntity = commentRepository.getReferenceById(comment.getParentId());
    }

    CommentEntity entity = CommentEntity.from(comment, parentEntity);
    CommentEntity savedEntity = commentRepository.save(entity);

    return savedEntity.toDomain();
  }

  // ========== LoadCommentPort Implementation ==========

  @Override
  public Optional<Comment> loadComment(Long commentId) {
    return commentRepository.findById(commentId).map(CommentEntity::toDomain);
  }

  @Override
  public Page<Comment> loadRootComments(Long postId, Pageable pageable) {
    return commentRepository
        .findRootCommentsByPostId(postId, pageable)
        .map(CommentEntity::toDomain);
  }

  @Override
  public Page<Comment> loadReplies(Long parentId, Pageable pageable) {
    return commentRepository.findRepliesByParentId(parentId, pageable).map(CommentEntity::toDomain);
  }

  /** 하드 딜리트 대상 ID 조회 */
  @Override
  public List<Long> loadCommentIdsForDeletion(LocalDateTime cutoff, int batchSize) {
    // Repository에 findIdsByIsDeletedTrueAndUpdatedAtBefore 메서드 필요
    return commentRepository.findIdsByIsDeletedTrueAndUpdatedAtBefore(
        cutoff, PageRequest.of(0, batchSize));
  }

  // ========== DeleteCommentPort Implementation ==========

  @Override
  @Transactional
  public void deleteAllByPostId(Long postId) {
    commentRepository.deleteAllByPostId(postId);
  }

  /** ID 리스트 기반 일괄 하드 딜리트 */
  @Override
  @Transactional
  public void deleteAllByIdInBatch(List<Long> commentIds) {

    // 1. 자식(대댓글) 먼저 삭제
    commentRepository.deleteByParentIdIn(commentIds);

    // 2.부모 삭제
    commentRepository.deleteAllByIdInBatch(commentIds);
  }
}
