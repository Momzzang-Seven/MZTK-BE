package momzzangseven.mztkbe.modules.comment.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.global.error.comment.CommentPostMismatchException;
import momzzangseven.mztkbe.modules.comment.application.dto.*;
import momzzangseven.mztkbe.modules.comment.application.port.in.*;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService
    implements CreateCommentUseCase,
        GetCommentUseCase,
        CountCommentsUseCase,
        UpdateCommentUseCase,
        DeleteCommentUseCase {

  private final LoadCommentPort loadCommentPort;
  private final SaveCommentPort saveCommentPort;
  private final LoadPostPort loadPostPort;
  private final DeleteCommentPort deleteCommentPort;
  private final GrantCommentXpPort grantCommentXpPort;
  private final LoadCommentWriterPort loadCommentWriterPort;

  // 1. 생성 (Create)
  @Override
  @Transactional
  public CommentMutationResult createComment(CreateCommentCommand command) {
    // 1-1. 게시글 존재 여부 검증 (외부 모듈 포트 사용)
    validatePostExists(command.postId());

    // 1-2. 대댓글인 경우 부모 댓글 검증
    if (command.parentId() != null) {
      validateParentComment(command.parentId(), command.postId());
    }

    // 1-3. 도메인 객체 생성 및 저장
    Comment newComment =
        Comment.create(command.postId(), command.userId(), command.parentId(), command.content());

    Comment savedComment = saveCommentPort.saveComment(newComment);

    try {
      grantCommentXpPort.grantCreateCommentXp(savedComment.getWriterId(), savedComment.getId());
    } catch (Exception e) {
      log.warn(
          "Comment created but XP grant failed for userId={}, commentId={}",
          savedComment.getWriterId(),
          savedComment.getId(),
          e);
    }

    return CommentMutationResult.from(savedComment);
  }

  // 2. 수정 (Update)
  @Override
  @Transactional
  public CommentMutationResult updateComment(UpdateCommentCommand command) {
    Comment comment = loadCommentOrThrow(command.commentId());

    comment.validateWriter(command.userId());

    comment.updateContent(command.content());

    return CommentMutationResult.from(saveCommentPort.saveComment(comment));
  }

  // 3-1. 삭제 (Delete - 사용자 요청)
  @Override
  @Transactional
  public void deleteComment(DeleteCommentCommand command) {
    Comment comment = loadCommentOrThrow(command.commentId());

    comment.validateWriter(command.userId());

    comment.delete();
    saveCommentPort.saveComment(comment);
  }

  // 3-2. 삭제 (Delete - 게시글 삭제 이벤트 수신용)
  @Override
  @Transactional(readOnly = false)
  public void deleteCommentsByPostId(Long postId) {
    // 해당 게시글의 모든 댓글 일괄 Soft Delete
    deleteCommentPort.deleteAllByPostId(postId);
  }

  // 4. 루트 댓글 조회 (Read)
  @Override
  public Page<CommentResult> getRootComments(GetRootCommentsQuery query) {
    validatePostExists(query.postId());
    return toResultPage(loadCommentPort.loadRootComments(query.postId(), query.pageable()), true);
  }

  // 5. 대댓글 조회 (Read)
  @Override
  public Page<CommentResult> getReplies(GetRepliesQuery query) {
    // 부모 댓글이 존재하는지 먼저 확인
    Comment parent =
        loadCommentPort.loadComment(query.parentId()).orElseThrow(CommentNotFoundException::new);
    validatePostExists(parent.getPostId());
    validateParentIsRootComment(parent);

    return toResultPage(loadCommentPort.loadReplies(query.parentId(), query.pageable()), false);
  }

  /** Counts all persisted comments for a post, including soft-deleted rows shown as masked text. */
  @Override
  public long countCommentsByPostId(Long postId) {
    return loadCommentPort.countCommentsByPostId(postId);
  }

  /** Counts all persisted comments for each post, including soft-deleted rows. */
  @Override
  public Map<Long, Long> countCommentsByPostIds(List<Long> postIds) {
    return loadCommentPort.countCommentsByPostIds(postIds);
  }

  // --- Private Helper Methods ---

  private Comment loadCommentOrThrow(Long commentId) {
    return loadCommentPort.loadComment(commentId).orElseThrow(CommentNotFoundException::new);
  }

  private void validateParentComment(Long parentId, Long postId) {
    Comment parent = loadCommentOrThrow(parentId);

    if (!parent.getPostId().equals(postId)) {
      throw new CommentPostMismatchException();
    }

    if (parent.isDeleted()) {
      throw new BusinessException(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT);
    }

    validateParentIsRootComment(parent);
  }

  private void validateParentIsRootComment(Comment parent) {
    if (parent.getParentId() != null) {
      throw new BusinessException(ErrorCode.COMMENT_DEPTH_EXCEEDED);
    }
  }

  private void validatePostExists(Long postId) {
    if (!loadPostPort.existsPost(postId)) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
  }

  private Page<CommentResult> toResultPage(Page<Comment> comments, boolean includeReplyCount) {
    List<Comment> content = comments.getContent();
    if (content.isEmpty()) {
      return comments.map(comment -> CommentResult.from(comment, null, 0L));
    }

    List<Long> commentIds = content.stream().map(Comment::getId).toList();
    Map<Long, Long> replyCounts =
        includeReplyCount ? loadCommentPort.countDirectRepliesByParentIds(commentIds) : Map.of();

    Set<Long> writerIds =
        content.stream()
            .filter(comment -> !comment.isDeleted())
            .map(Comment::getWriterId)
            .collect(Collectors.toSet());
    Map<Long, WriterSummary> writers = loadCommentWriterPort.loadWritersByIds(writerIds);

    return comments.map(
        comment ->
            CommentResult.from(
                comment,
                writers.get(comment.getWriterId()),
                replyCounts.getOrDefault(comment.getId(), 0L)));
  }
}
