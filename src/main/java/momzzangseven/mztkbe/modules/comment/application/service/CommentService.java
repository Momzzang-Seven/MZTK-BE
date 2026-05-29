package momzzangseven.mztkbe.modules.comment.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.global.error.comment.CommentPostMismatchException;
import momzzangseven.mztkbe.global.error.comment.CommentTargetMismatchException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.comment.application.dto.*;
import momzzangseven.mztkbe.modules.comment.application.port.in.*;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService
    implements GetCommentUseCase,
        GetCommentCursorUseCase,
        UpdateCommentUseCase,
        DeleteCommentUseCase {

  private final LoadCommentPort loadCommentPort;
  private final SaveCommentPort saveCommentPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteCommentPort deleteCommentPort;
  private final LoadCommentWriterPort loadCommentWriterPort;

  /**
   * Persists a comment (T1). XP granting is orchestrated separately by {@code CreateCommentFacade}
   * so the request never holds two DB connections at once.
   */
  @Transactional
  public CommentMutationResult createComment(CreateCommentCommand command) {
    LoadAnswerPort.AnswerCommentContext answerContext =
        CommentTargetType.ANSWER.equals(command.targetType())
            ? validateAnswerWritable(command.answerId(), AnswerCommentMutation.CREATE)
            : null;
    Long targetPostId =
        CommentTargetType.ANSWER.equals(command.targetType())
            ? answerContext.postId()
            : command.postId();
    if (!CommentTargetType.ANSWER.equals(command.targetType())) {
      validatePostWritable(targetPostId);
    }

    // 1-2. 대댓글인 경우 부모 댓글 검증
    if (command.parentId() != null) {
      validateParentComment(
          command.parentId(), command.targetType(), command.postId(), command.answerId());
    }

    // 1-3. 도메인 객체 생성 및 저장
    Comment newComment =
        CommentTargetType.ANSWER.equals(command.targetType())
            ? Comment.createForAnswer(
                targetPostId,
                command.answerId(),
                command.userId(),
                command.parentId(),
                command.content())
            : Comment.createForPost(
                command.postId(), command.userId(), command.parentId(), command.content());

    Comment savedComment = saveCommentPort.saveComment(newComment);

    return CommentMutationResult.from(savedComment);
  }

  // 2. 수정 (Update)
  @Override
  @Transactional
  public CommentMutationResult updateComment(UpdateCommentCommand command) {
    Comment comment = loadCommentForUpdateOrThrow(command.commentId());
    return updateLoadedComment(comment, command.userId(), command.content());
  }

  @Override
  @Transactional
  public CommentMutationResult updateAnswerComment(UpdateAnswerCommentCommand command) {
    Comment comment = loadCommentForUpdateOrThrow(command.commentId());
    validateAnswerScopedMutationAccess(comment, command.userId(), AnswerCommentMutation.UPDATE);
    validateAnswerCommentTarget(comment, command.answerId());
    comment.updateContent(command.content());
    return CommentMutationResult.from(saveCommentPort.saveComment(comment));
  }

  // 3-1. 삭제 (Delete - 사용자 요청)
  @Override
  @Transactional
  public void deleteComment(DeleteCommentCommand command) {
    Comment comment = loadCommentForUpdateOrThrow(command.commentId());
    deleteLoadedComment(comment, command.userId());
  }

  @Override
  @Transactional
  public void deleteAnswerComment(DeleteAnswerCommentCommand command) {
    Comment comment = loadCommentForUpdateOrThrow(command.commentId());
    validateAnswerScopedMutationAccess(comment, command.userId(), AnswerCommentMutation.DELETE);
    validateAnswerCommentTarget(comment, command.answerId());
    comment.delete();
    saveCommentPort.saveComment(comment);
  }

  // 3-2. 삭제 (Delete - 게시글 삭제 이벤트 수신용)
  @Override
  @Transactional(readOnly = false)
  public void softDeleteAllCommentsByRootPostId(Long postId) {
    deleteCommentPort.softDeleteAllByRootPostId(postId);
  }

  @Override
  @Transactional(readOnly = false)
  public void deleteCommentsByAnswerId(Long answerId) {
    deleteCommentPort.deleteAllByAnswerId(answerId);
  }

  // 4. 루트 댓글 조회 (Read)
  @Override
  public Page<CommentResult> getRootComments(GetRootCommentsQuery query) {
    validatePostReadable(query.postId(), query.requesterUserId());
    return toResultPage(loadCommentPort.loadRootComments(query.postId(), query.pageable()), true);
  }

  @Override
  public Page<CommentResult> getAnswerRootComments(GetAnswerRootCommentsQuery query) {
    Long postId = loadAnswerPostIdOrThrow(query.answerId());
    validatePostReadable(postId, query.requesterUserId());
    return toResultPage(
        loadCommentPort.loadRootCommentsByAnswerId(query.answerId(), query.pageable()), true);
  }

  // 5. 대댓글 조회 (Read)
  @Override
  public Page<CommentResult> getReplies(GetRepliesQuery query) {
    // 부모 댓글이 존재하는지 먼저 확인
    Comment parent =
        loadCommentPort.loadComment(query.parentId()).orElseThrow(CommentNotFoundException::new);
    validateCommentTargetReadable(parent, query.requesterUserId());
    validateParentIsRootComment(parent);

    return toResultPage(loadCommentPort.loadReplies(query.parentId(), query.pageable()), false);
  }

  @Override
  public GetCommentsCursorResult getRootCommentsByCursor(GetRootCommentsCursorQuery query) {
    validatePostReadable(query.postId(), query.requesterUserId());
    List<Comment> comments =
        loadCommentPort.loadRootCommentsByCursor(query.postId(), query.pageRequest());
    boolean hasNext = comments.size() > query.pageRequest().size();
    List<Comment> pageComments =
        hasNext ? comments.subList(0, query.pageRequest().size()) : comments;
    String nextCursor =
        hasNext
            ? createNextCursor(
                pageComments.get(pageComments.size() - 1), query.pageRequest().scope())
            : null;
    return new GetCommentsCursorResult(toResultList(pageComments, true), hasNext, nextCursor);
  }

  @Override
  public GetCommentsCursorResult getAnswerRootCommentsByCursor(
      GetAnswerRootCommentsCursorQuery query) {
    Long postId = loadAnswerPostIdOrThrow(query.answerId());
    validatePostReadable(postId, query.requesterUserId());
    List<Comment> comments =
        loadCommentPort.loadRootCommentsByAnswerIdCursor(query.answerId(), query.pageRequest());
    boolean hasNext = comments.size() > query.pageRequest().size();
    List<Comment> pageComments =
        hasNext ? comments.subList(0, query.pageRequest().size()) : comments;
    String nextCursor =
        hasNext
            ? createNextCursor(
                pageComments.get(pageComments.size() - 1), query.pageRequest().scope())
            : null;
    return new GetCommentsCursorResult(toResultList(pageComments, true), hasNext, nextCursor);
  }

  @Override
  public GetCommentsCursorResult getRepliesByCursor(GetRepliesCursorQuery query) {
    Comment parent =
        loadCommentPort.loadComment(query.parentId()).orElseThrow(CommentNotFoundException::new);
    validateCommentTargetReadable(parent, query.requesterUserId());
    validateParentIsRootComment(parent);

    List<Comment> comments =
        loadCommentPort.loadRepliesByCursor(query.parentId(), query.pageRequest());
    boolean hasNext = comments.size() > query.pageRequest().size();
    List<Comment> pageComments =
        hasNext ? comments.subList(0, query.pageRequest().size()) : comments;
    String nextCursor =
        hasNext
            ? createNextCursor(
                pageComments.get(pageComments.size() - 1), query.pageRequest().scope())
            : null;
    return new GetCommentsCursorResult(toResultList(pageComments, false), hasNext, nextCursor);
  }

  // --- Private Helper Methods ---

  private Comment loadCommentOrThrow(Long commentId) {
    return loadCommentPort.loadComment(commentId).orElseThrow(CommentNotFoundException::new);
  }

  private Comment loadCommentForUpdateOrThrow(Long commentId) {
    return loadCommentPort
        .loadCommentForUpdate(commentId)
        .orElseThrow(CommentNotFoundException::new);
  }

  private CommentMutationResult updateLoadedComment(Comment comment, Long userId, String content) {
    validateCommentNotDeleted(comment);
    validateCommentTargetWritable(comment, AnswerCommentMutation.UPDATE);
    comment.validateWriter(userId);
    comment.updateContent(content);
    return CommentMutationResult.from(saveCommentPort.saveComment(comment));
  }

  private void deleteLoadedComment(Comment comment, Long userId) {
    validateCommentNotDeleted(comment);
    validateCommentTargetWritable(comment, AnswerCommentMutation.DELETE);
    comment.validateWriter(userId);
    comment.delete();
    saveCommentPort.saveComment(comment);
  }

  private void validateAnswerScopedMutationAccess(
      Comment comment, Long userId, AnswerCommentMutation mutation) {
    validateCommentNotDeleted(comment);
    comment.validateWriter(userId);
    validateCommentTargetWritable(comment, mutation);
  }

  private void validateCommentNotDeleted(Comment comment) {
    if (comment.isDeleted()) {
      throw new BusinessException(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT);
    }
  }

  private void validateAnswerCommentTarget(Comment comment, Long answerId) {
    if (!CommentTargetType.ANSWER.equals(comment.getTargetType())
        || !answerId.equals(comment.getAnswerId())) {
      throw new CommentTargetMismatchException();
    }
  }

  private void validateParentComment(
      Long parentId, CommentTargetType targetType, Long postId, Long answerId) {
    Comment parent = loadCommentOrThrow(parentId);

    if (CommentTargetType.ANSWER.equals(targetType)) {
      if (!parent.getTargetType().equals(targetType) || !parent.getAnswerId().equals(answerId)) {
        throw new CommentTargetMismatchException();
      }
    } else if (!parent.getTargetType().equals(targetType) || !parent.getPostId().equals(postId)) {
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

  private void validatePostReadable(Long postId, Long requesterUserId) {
    LoadPostPort.PostVisibilityContext context = loadPostVisibilityOrThrow(postId);
    if (!context.readableBy(requesterUserId)) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
  }

  private void validatePostWritable(Long postId) {
    LoadPostPort.PostVisibilityContext context = loadPostVisibilityOrThrow(postId);
    if (!context.writable()) {
      throw new BusinessException(
          ErrorCode.INVALID_POST_INPUT, "Post is not in a state that allows comment interactions.");
    }
  }

  private void validateCommentTargetReadable(Comment comment, Long requesterUserId) {
    Long postId =
        CommentTargetType.ANSWER.equals(comment.getTargetType())
            ? loadAnswerPostIdOrThrow(comment.getAnswerId())
            : comment.getPostId();
    validatePostReadable(postId, requesterUserId);
  }

  private void validateCommentTargetWritable(Comment comment, AnswerCommentMutation mutation) {
    if (CommentTargetType.ANSWER.equals(comment.getTargetType())) {
      validateAnswerWritable(comment.getAnswerId(), mutation);
      return;
    }
    validatePostWritable(comment.getPostId());
  }

  private LoadAnswerPort.AnswerCommentContext validateAnswerWritable(
      Long answerId, AnswerCommentMutation mutation) {
    LoadAnswerPort.AnswerCommentContext context = loadAnswerContextForUpdateOrThrow(answerId);
    validatePostWritable(context.postId());
    validateAnswerCommentMutationAllowed(context, mutation);
    return context;
  }

  private void validateAnswerCommentMutationAllowed(
      LoadAnswerPort.AnswerCommentContext context, AnswerCommentMutation mutation) {
    if (!context.answerLocked()) {
      return;
    }
    switch (mutation) {
      case CREATE -> throw new CannotAnswerSolvedPostException();
      case UPDATE -> throw new CannotUpdateAnswerOnSolvedPostException();
      case DELETE -> throw new CannotDeleteAnswerOnSolvedPostException();
    }
  }

  private Long loadAnswerPostIdOrThrow(Long answerId) {
    return loadAnswerPort
        .loadAnswerCommentContext(answerId)
        .map(LoadAnswerPort.AnswerCommentContext::postId)
        .orElseThrow(AnswerNotFoundException::new);
  }

  private LoadAnswerPort.AnswerCommentContext loadAnswerContextForUpdateOrThrow(Long answerId) {
    return loadAnswerPort
        .loadAnswerCommentContextForUpdate(answerId)
        .orElseThrow(AnswerNotFoundException::new);
  }

  private LoadPostPort.PostVisibilityContext loadPostVisibilityOrThrow(Long postId) {
    return loadPostPort
        .loadPostVisibilityContext(postId)
        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
  }

  private enum AnswerCommentMutation {
    CREATE,
    UPDATE,
    DELETE
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

  private List<CommentResult> toResultList(List<Comment> comments, boolean includeReplyCount) {
    if (comments.isEmpty()) {
      return List.of();
    }

    List<Long> commentIds = comments.stream().map(Comment::getId).toList();
    Map<Long, Long> replyCounts =
        includeReplyCount ? loadCommentPort.countDirectRepliesByParentIds(commentIds) : Map.of();

    Set<Long> writerIds =
        comments.stream()
            .filter(comment -> !comment.isDeleted())
            .map(Comment::getWriterId)
            .collect(Collectors.toSet());
    Map<Long, WriterSummary> writers = loadCommentWriterPort.loadWritersByIds(writerIds);

    return comments.stream()
        .map(
            comment ->
                CommentResult.from(
                    comment,
                    writers.get(comment.getWriterId()),
                    replyCounts.getOrDefault(comment.getId(), 0L)))
        .toList();
  }

  private String createNextCursor(Comment comment, String scope) {
    return CursorCodec.encode(new KeysetCursor(comment.getCreatedAt(), comment.getId(), scope));
  }
}
