package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentPersistenceAdapter implements LoadCommentPort, SaveCommentPort {

  private final CommentJpaRepository commentRepository;

  @Override
  @Transactional // 쓰기 트랜잭션
  public Comment saveComment(Comment comment) {
    CommentEntity parentEntity = null;

    if (comment.getParentId() != null) {
      parentEntity = commentRepository.getReferenceById(comment.getParentId());
    }

    // 도메인 -> 엔티티 변환
    CommentEntity entity = CommentEntity.from(comment, parentEntity);

    // 저장
    CommentEntity savedEntity = commentRepository.save(entity);

    // 엔티티 -> 도메인 변환 후 반환
    return savedEntity.toDomain();
  }

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
}
