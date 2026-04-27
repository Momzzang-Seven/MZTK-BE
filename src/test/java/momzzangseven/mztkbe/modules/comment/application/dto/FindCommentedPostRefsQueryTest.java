package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FindCommentedPostRefsQuery unit test")
class FindCommentedPostRefsQueryTest {

  @Test
  @DisplayName("normalizes post type and question search")
  void normalize_questionSearch() {
    FindCommentedPostRefsQuery query =
        new FindCommentedPostRefsQuery(1L, " question ", " FoRm ", pageRequest());

    assertThatCode(query::validate).doesNotThrowAnyException();
    assertThat(query.normalizedPostType()).isEqualTo("QUESTION");
    assertThat(query.normalizedSearch()).isEqualTo("form");
  }

  @Test
  @DisplayName("ignores search for FREE posts")
  void normalizedSearch_ignoresFreeSearch() {
    FindCommentedPostRefsQuery query =
        new FindCommentedPostRefsQuery(1L, "FREE", " FoRm ", pageRequest());

    assertThat(query.normalizedSearch()).isNull();
  }

  @Test
  @DisplayName("rejects unsupported post type")
  void validate_rejectsUnsupportedType() {
    FindCommentedPostRefsQuery query =
        new FindCommentedPostRefsQuery(1L, "ANSWER", null, pageRequest());

    assertThatThrownBy(query::validate).isInstanceOf(IllegalArgumentException.class);
  }

  private CursorPageRequest pageRequest() {
    return new CursorPageRequest(null, 10, "scope");
  }
}
