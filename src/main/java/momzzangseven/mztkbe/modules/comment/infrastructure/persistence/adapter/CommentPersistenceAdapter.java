package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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

  @Override
  public Map<Long, Long> countDirectRepliesByParentIds(List<Long> parentIds) {
    if (parentIds == null || parentIds.isEmpty()) {
      return Map.of();
    }

    return commentRepository.countDirectRepliesByParentIds(parentIds).stream()
        .collect(
            Collectors.toMap(
                CommentJpaRepository.DirectReplyCount::getParentId,
                CommentJpaRepository.DirectReplyCount::getReplyCount));
  }

  @Override
  public List<Long> loadCommentIdsForDeletion(LocalDateTime cutoff, int batchSize) {
    return commentRepository.findIdsByIsDeletedTrueAndUpdatedAtBefore(
        cutoff, PageRequest.of(0, batchSize));
  }

  // ========== DeleteCommentPort Implementation ==========

  @Override
  @Transactional(readOnly = false)
  public void deleteAllByPostId(Long postId) {
    commentRepository.deleteAllByPostId(postId);
  }

  @Override
  @Transactional
  public void deleteAllById(List<Long> commentIds) {
    if (commentIds == null || commentIds.isEmpty()) {
      return;
    }

    // 1. 자식(대댓글) 먼저 삭제
    commentRepository.deleteByParentIdIn(commentIds);

    // 2. 부모 삭제 (JPA 최적화 기술 활용)
    commentRepository.deleteAllByIdInBatch(commentIds);
  }
}
