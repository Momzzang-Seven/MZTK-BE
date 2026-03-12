package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostSearchCondition unit test")
class PostSearchConditionTest {

  @Test
  @DisplayName("factory method stores all values")
  void ofStoresValues() {
    PostSearchCondition condition =
        PostSearchCondition.of(PostType.QUESTION, "java", "query", 2, 30);

    assertThat(condition.type()).isEqualTo(PostType.QUESTION);
    assertThat(condition.tagName()).isEqualTo("java");
    assertThat(condition.search()).isEqualTo("query");
    assertThat(condition.page()).isEqualTo(2);
    assertThat(condition.size()).isEqualTo(30);
  }

  @Test
  @DisplayName("record equality is value based")
  void recordEquality() {
    PostSearchCondition first = PostSearchCondition.of(PostType.FREE, null, null, 0, 20);
    PostSearchCondition same = new PostSearchCondition(PostType.FREE, null, null, 0, 20);

    assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
  }
}
