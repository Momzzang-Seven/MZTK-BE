package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyPostsV2Request unit test")
class GetMyPostsV2RequestTest {

  @Test
  @DisplayName("converts query parameters to command")
  void toCommand() {
    var command =
        new GetMyPostsV2Request(PostType.QUESTION, " Squat ", " Form ", "cursor", 20).toCommand(7L);

    assertThat(command.requesterId()).isEqualTo(7L);
    assertThat(command.type()).isEqualTo(PostType.QUESTION);
    assertThat(command.tag()).isEqualTo(" Squat ");
    assertThat(command.search()).isEqualTo(" Form ");
    assertThat(command.effectiveSearch()).isEqualTo("form");
    assertThat(command.cursor()).isEqualTo("cursor");
    assertThat(command.size()).isEqualTo(20);
  }
}
