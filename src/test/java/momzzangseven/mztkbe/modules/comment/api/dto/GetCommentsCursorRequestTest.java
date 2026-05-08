package momzzangseven.mztkbe.modules.comment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetCommentsCursorRequest unit test")
class GetCommentsCursorRequestTest {

  @Test
  @DisplayName("uses root comment default size")
  void rootDefaultSize() {
    var query = new GetCommentsCursorRequest(null, null).toRootQuery(1L);

    assertThat(query.pageRequest().size()).isEqualTo(20);
  }

  @Test
  @DisplayName("uses answer root comment default size and scope")
  void answerRootDefaultSizeAndScope() {
    var query = new GetCommentsCursorRequest(null, null).toAnswerRootQuery(300L, 1L);

    assertThat(query.pageRequest().size()).isEqualTo(20);
    assertThat(query.pageRequest().scope()).isEqualTo(CursorScope.answerRootComments(300L));
  }

  @Test
  @DisplayName("uses reply default size")
  void replyDefaultSize() {
    var query = new GetCommentsCursorRequest(null, null).toRepliesQuery(1L);

    assertThat(query.pageRequest().size()).isEqualTo(10);
  }

  @Test
  @DisplayName("rejects size over max")
  void rejectsSizeOverMax() {
    assertThatThrownBy(() -> new GetCommentsCursorRequest(null, 51).toRootQuery(1L))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("answer root query decodes matching answer cursor scope")
  void answerRootQuery_decodesMatchingAnswerCursorScope() {
    String scope = CursorScope.answerRootComments(300L);
    String cursor =
        CursorCodec.encode(new KeysetCursor(LocalDateTime.of(2026, 4, 24, 12, 0), 10L, scope));

    var query = new GetCommentsCursorRequest(cursor, 5).toAnswerRootQuery(300L, 1L);

    assertThat(query.pageRequest().scope()).isEqualTo(scope);
    assertThat(query.pageRequest().cursor().id()).isEqualTo(10L);
  }
}
