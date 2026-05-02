package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.comment.application.dto.BanManagedCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BanManagedCommentService 단위 테스트")
class BanManagedCommentServiceTest {

  @Mock private LoadCommentPort loadCommentPort;
  @Mock private SaveCommentPort saveCommentPort;

  @InjectMocks private BanManagedCommentService service;

  @Test
  @DisplayName("삭제되지 않은 댓글은 soft delete 처리한다")
  void execute_activeComment_softDeletes() {
    Comment comment = comment(false);
    given(loadCommentPort.loadComment(31L)).willReturn(Optional.of(comment));
    given(saveCommentPort.saveComment(org.mockito.Mockito.any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var result = service.execute(new BanManagedCommentCommand(31L));

    assertThat(result.commentId()).isEqualTo(31L);
    assertThat(result.postId()).isEqualTo(21L);
    assertThat(result.moderated()).isTrue();

    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(saveCommentPort).saveComment(captor.capture());
    assertThat(captor.getValue().isDeleted()).isTrue();
    assertThat(captor.getValue().getContent()).isEqualTo("삭제된 댓글입니다.");
  }

  @Test
  @DisplayName("이미 삭제된 댓글은 idempotent 성공으로 처리하고 재저장하지 않는다")
  void execute_deletedComment_idempotentSuccess() {
    given(loadCommentPort.loadComment(31L)).willReturn(Optional.of(comment(true)));

    var result = service.execute(new BanManagedCommentCommand(31L));

    assertThat(result.commentId()).isEqualTo(31L);
    assertThat(result.postId()).isEqualTo(21L);
    assertThat(result.moderated()).isFalse();
    verify(saveCommentPort, never()).saveComment(org.mockito.Mockito.any(Comment.class));
  }

  private Comment comment(boolean deleted) {
    return Comment.builder()
        .id(31L)
        .postId(21L)
        .writerId(7L)
        .parentId(null)
        .content(deleted ? "삭제된 댓글입니다." : "content")
        .isDeleted(deleted)
        .createdAt(LocalDateTime.parse("2025-01-01T00:00:00"))
        .updatedAt(LocalDateTime.parse("2025-01-01T00:00:00"))
        .build();
  }
}
