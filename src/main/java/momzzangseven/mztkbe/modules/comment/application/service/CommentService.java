package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.global.error.comment.CommentPostMismatchException;
import momzzangseven.mztkbe.modules.comment.application.dto.*;
import momzzangseven.mztkbe.modules.comment.application.port.in.*;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService
    implements CreateCommentUseCase, GetCommentUseCase, UpdateCommentUseCase, DeleteCommentUseCase {

  private final LoadCommentPort loadCommentPort;
  private final SaveCommentPort saveCommentPort;
  private final LoadPostPort loadPostPort;

  // 1. 생성 (Create)
  @Override
  @Transactional
  public CommentResult createComment(CreateCommentCommand command) {
    // 1-1. 게시글 존재 여부 검증 (외부 모듈 포트 사용)
    if (!loadPostPort.existsPost(command.postId())) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }

    // 1-2. 대댓글인 경우 부모 댓글 검증
    if (command.parentId() != null) {
      validateParentComment(command.parentId(), command.postId());
    }

    // 1-3. 도메인 객체 생성 및 저장
    Comment newComment =
        Comment.create(command.postId(), command.userId(), command.parentId(), command.content());

    return CommentResult.from(saveCommentPort.saveComment(newComment));
  }

  // 2. 수정 (Update)
  @Override
  @Transactional
  public CommentResult updateComment(UpdateCommentCommand command) {
    Comment comment = loadCommentOrThrow(command.commentId());

    comment.validateWriter(command.userId());

    comment.updateContent(command.content());

    return CommentResult.from(saveCommentPort.saveComment(comment));
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
  @Transactional
  public void deleteCommentsByPostId(Long postId) {
    // 해당 게시글의 모든 댓글 일괄 Soft Delete
    saveCommentPort.deleteAllByPostId(postId);
  }

  // 4. 루트 댓글 조회 (Read)
  @Override
  public Page<CommentResult> getRootComments(GetRootCommentsQuery query) {
    return loadCommentPort
        .loadRootComments(query.postId(), query.pageable())
        .map(CommentResult::from);
  }

  // 5. 대댓글 조회 (Read)
  @Override
  public Page<CommentResult> getReplies(GetRepliesQuery query) {
    // 부모 댓글이 존재하는지 먼저 확인
    if (loadCommentPort.loadComment(query.parentId()).isEmpty()) {
      throw new CommentNotFoundException();
    }

    return loadCommentPort.loadReplies(query.parentId(), query.pageable()).map(CommentResult::from);
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
  }
}
