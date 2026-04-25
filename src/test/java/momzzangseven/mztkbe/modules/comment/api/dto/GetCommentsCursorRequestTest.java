package momzzangseven.mztkbe.modules.comment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
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
}
