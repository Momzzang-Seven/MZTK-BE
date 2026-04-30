package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetPostsV2Request unit test")
class GetPostsV2RequestTest {

  @Test
  @DisplayName("converts query parameters to cursor condition")
  void toCommand() {
    var condition =
        new GetPostsV2Request(PostType.QUESTION, " Squat ", " FoRm ", null, 30).toCommand();

    assertThat(condition.type()).isEqualTo(PostType.QUESTION);
    assertThat(condition.tagName()).isEqualTo("squat");
    assertThat(condition.search()).isEqualTo("form");
    assertThat(condition.size()).isEqualTo(30);
  }
}
