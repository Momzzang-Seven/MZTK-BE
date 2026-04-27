package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyCommentedPostsV2Request unit test")
class GetMyCommentedPostsV2RequestTest {

  @Test
  @DisplayName("toCommand() preserves search, cursor, and size")
  void toCommand_preservesRequestFields() {
    var command =
        new GetMyCommentedPostsV2Request(PostType.QUESTION, " form ", "cursor", 20).toCommand(7L);

    assertThat(command.requesterId()).isEqualTo(7L);
    assertThat(command.type()).isEqualTo(PostType.QUESTION);
    assertThat(command.search()).isEqualTo(" form ");
    assertThat(command.cursor()).isEqualTo("cursor");
    assertThat(command.size()).isEqualTo(20);
  }
}
