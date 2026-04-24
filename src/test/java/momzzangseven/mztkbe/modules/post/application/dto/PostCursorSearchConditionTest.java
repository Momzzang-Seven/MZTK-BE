package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostCursorSearchCondition unit test")
class PostCursorSearchConditionTest {

  @Test
  @DisplayName("normalizes tag and applies default size")
  void normalizesTagAndDefaultSize() {
    PostCursorSearchCondition condition =
        PostCursorSearchCondition.of(PostType.QUESTION, " Squat ", " FoRm ", null, null);

    assertThat(condition.tagName()).isEqualTo("squat");
    assertThat(condition.search()).isEqualTo("form");
    assertThat(condition.size()).isEqualTo(10);
  }

  @Test
  @DisplayName("ignores search for FREE posts")
  void ignoresSearchForFreePosts() {
    PostCursorSearchCondition condition =
        PostCursorSearchCondition.of(PostType.FREE, null, "ignored", null, 20);

    assertThat(condition.search()).isNull();
    assertThat(condition.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("rejects size over max")
  void rejectsSizeOverMax() {
    assertThatThrownBy(() -> PostCursorSearchCondition.of(PostType.QUESTION, null, null, null, 51))
        .isInstanceOf(InvalidCursorException.class);
  }
}
