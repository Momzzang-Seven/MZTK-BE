package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.in.CreateCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService implements CreateCommentUseCase, GetCommentUseCase {

  private final LoadCommentPort loadCommentPort;
  private final SaveCommentPort saveCommentPort;

  /** 댓글 생성 (루트 댓글 & 대댓글 공통) */
  @Override
  @Transactional // 쓰기 트랜잭션 필수
  public Comment createComment(CreateCommentCommand command) {
    // 1. 대댓글인 경우, 부모 댓글이 존재하는지 검증
    if (command.parentId() != null) {
      Comment parent =
          loadCommentPort
              .loadComment(command.parentId())
              .orElseThrow(() -> new IllegalArgumentException("부모 댓글을 찾을 수 없습니다."));

      if (parent.isDeleted()) {
        throw new IllegalArgumentException("삭제된 댓글에는 답글을 달 수 없습니다.");
      }
    }

    // 2. 도메인 객체 생성 (팩토리 메서드 활용)
    Comment newComment =
        Comment.create(command.postId(), command.writerId(), command.parentId(), command.content());

    // 3. 저장 (Output Port 호출)
    return saveCommentPort.saveComment(newComment);
  }

  /** 루트 댓글 목록 조회 */
  @Override
  public Page<Comment> getRootComments(Long postId, Pageable pageable) {
    return loadCommentPort.loadRootComments(postId, pageable);
  }

  /** 대댓글 목록 조회 */
  @Override
  public Page<Comment> getReplies(Long parentId, Pageable pageable) {
    if (loadCommentPort.loadComment(parentId).isEmpty()) {
      throw new IllegalArgumentException("존재하지 않는 댓글입니다.");
    }

    return loadCommentPort.loadReplies(parentId, pageable);
  }
}
