package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyLikedPostsV2Request unit test")
class GetMyLikedPostsV2RequestTest {

  @Test
  @DisplayName("converts query parameters to command")
  void toCommand() {
    var command = new GetMyLikedPostsV2Request(PostType.FREE, "cursor", 20).toCommand(7L);

    assertThat(command.requesterId()).isEqualTo(7L);
    assertThat(command.type()).isEqualTo(PostType.FREE);
    assertThat(command.cursor()).isEqualTo("cursor");
    assertThat(command.size()).isEqualTo(20);
  }
}
