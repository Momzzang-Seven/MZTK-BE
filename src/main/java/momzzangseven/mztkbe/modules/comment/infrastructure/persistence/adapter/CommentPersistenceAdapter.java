package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardPostCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.LatestCommentedPostRef;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentView;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadManagedBoardPostCommentsPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentPersistenceAdapter
    implements LoadCommentPort,
        SaveCommentPort,
        DeleteCommentPort,
        LoadManagedBoardPostCommentsPort {

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
  public List<Comment> loadRootCommentsByCursor(Long postId, CursorPageRequest pageRequest) {
    Pageable pageable = PageRequest.of(0, pageRequest.limitWithProbe());
    List<CommentEntity> entities =
        pageRequest.hasCursor()
            ? commentRepository.findRootCommentsByPostIdAfterCursor(
                postId, pageRequest.cursor().createdAt(), pageRequest.cursor().id(), pageable)
            : commentRepository.findRootCommentsByPostIdFirstPage(postId, pageable);
    return entities.stream().map(CommentEntity::toDomain).toList();
  }

  @Override
  public List<Comment> loadRepliesByCursor(Long parentId, CursorPageRequest pageRequest) {
    Pageable pageable = PageRequest.of(0, pageRequest.limitWithProbe());
    List<CommentEntity> entities =
        pageRequest.hasCursor()
            ? commentRepository.findRepliesByParentIdAfterCursor(
                parentId, pageRequest.cursor().createdAt(), pageRequest.cursor().id(), pageable)
            : commentRepository.findRepliesByParentIdFirstPage(parentId, pageable);
    return entities.stream().map(CommentEntity::toDomain).toList();
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
  public Map<Long, Long> countCommentsByPostIds(List<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Map.of();
    }

    return commentRepository.countCommentsByPostIds(postIds).stream()
        .collect(
            Collectors.toMap(
                CommentJpaRepository.PostCommentCount::getPostId,
                CommentJpaRepository.PostCommentCount::getCommentCount));
  }

  @Override
  public Map<Long, Long> countCommentsByUserIds(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }

    return commentRepository.countCommentsByUserIds(userIds).stream()
        .collect(
            Collectors.toMap(
                CommentJpaRepository.UserCommentCount::getUserId,
                CommentJpaRepository.UserCommentCount::getCommentCount));
  }

  @Override
  public Page<ManagedBoardCommentView> load(GetManagedBoardPostCommentsQuery query) {
    Pageable pageable =
        PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return commentRepository.findByPostId(query.postId(), pageable).map(this::toManagedView);
  }

  @Override
  public List<LatestCommentedPostRef> findCommentedPostRefsByUserCursor(
      FindCommentedPostRefsQuery query) {
    query.validate();
    String searchPattern = LikePatternEscaper.escape(query.normalizedSearch());
    List<CommentJpaRepository.CommentedPostRefProjection> refs =
        query.pageRequest().hasCursor()
            ? commentRepository.findCommentedPostRefsAfterCursor(
                query.requesterId(),
                query.normalizedPostType(),
                searchPattern,
                query.pageRequest().cursor().createdAt(),
                query.pageRequest().cursor().id(),
                query.pageRequest().limitWithProbe())
            : commentRepository.findCommentedPostRefsFirstPage(
                query.requesterId(),
                query.normalizedPostType(),
                searchPattern,
                query.pageRequest().limitWithProbe());
    return refs.stream()
        .map(
            ref ->
                new LatestCommentedPostRef(
                    ref.getPostId(), ref.getLatestCommentId(), ref.getLatestCommentedAt()))
        .toList();
  }

  @Override
  public long countCommentsByPostId(Long postId) {
    if (postId == null) {
      return 0L;
    }
    return commentRepository.countByPostId(postId);
  }

  @Override
  public List<Long> loadCommentIdsForDeletion(LocalDateTime cutoff, int batchSize) {
    return commentRepository.findIdsByIsDeletedTrueAndUpdatedAtBefore(
        cutoff, PageRequest.of(0, batchSize));
  }

  private ManagedBoardCommentView toManagedView(CommentEntity entity) {
    Comment comment = entity.toDomain();
    return new ManagedBoardCommentView(
        comment.getId(),
        comment.getPostId(),
        comment.getWriterId(),
        comment.getContent(),
        comment.getParentId(),
        comment.isDeleted(),
        comment.getCreatedAt());
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
