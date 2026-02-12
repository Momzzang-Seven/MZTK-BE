package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.global.error.comment.CommentPostMismatchException;
import momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException;
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

  // 1. 생성 (Create) - Command 사용
  @Override
  @Transactional
  public CommentResult createComment(CreateCommentCommand command) {
    // 1. 게시글 존재 여부 검증
    if (!loadPostPort.existsPost(command.postId())) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }

    // 2. 대댓글인 경우 부모 댓글 관련 검증
    if (command.parentId() != null) {
      Comment parent =
          loadCommentPort
              .loadComment(command.parentId())
              .orElseThrow(() -> new CommentNotFoundException());

      // 3. 부모 댓글의 postId와 요청 postId 일치 여부 검증
      if (!parent.getPostId().equals(command.postId())) {
        throw new CommentPostMismatchException();
      }

      if (parent.isDeleted()) {
        throw new BusinessException(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT);
      }
    }

    Comment newComment =
        Comment.create(command.postId(), command.userId(), command.parentId(), command.content());
    Comment savedComment = saveCommentPort.saveComment(newComment);

    return CommentResult.from(savedComment);
  }

  // 2. 수정 (Update)
  @Override
  @Transactional
  public CommentResult updateComment(UpdateCommentCommand command) {
    Comment comment =
        loadCommentPort
            .loadComment(command.commentId())
            .orElseThrow(() -> new CommentNotFoundException());

    if (!comment.getWriterId().equals(command.userId())) {
      throw new CommentUnauthorizedException();
    }

    comment.updateContent(command.content());

    return CommentResult.from(saveCommentPort.saveComment(comment));
  }

  // 3. 삭제 (Delete)
  @Override
  @Transactional
  public void deleteComment(DeleteCommentCommand command) {
    Comment comment =
        loadCommentPort
            .loadComment(command.commentId())
            .orElseThrow(() -> new CommentNotFoundException());

    if (!comment.getWriterId().equals(command.userId())) {
      throw new CommentUnauthorizedException();
    }

    comment.delete();
    saveCommentPort.saveComment(comment);
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
    if (loadCommentPort.loadComment(query.parentId()).isEmpty()) {
      throw new CommentNotFoundException();
    }

    return loadCommentPort.loadReplies(query.parentId(), query.pageable()).map(CommentResult::from);
  }
}
