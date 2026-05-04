package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModeratePostCommand unit test")
class ModeratePostCommandTest {

  @Test
  @DisplayName("validate accepts positive operatorId and postId")
  void validate_acceptsPositiveIds() {
    ModeratePostCommand command = new ModeratePostCommand(99L, 10L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate rejects invalid operatorId")
  void validate_rejectsInvalidOperatorId() {
    ModeratePostCommand command = new ModeratePostCommand(0L, 10L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");
  }

  @Test
  @DisplayName("validate rejects invalid postId")
  void validate_rejectsInvalidPostId() {
    ModeratePostCommand command = new ModeratePostCommand(99L, 0L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("postId must be positive");
  }
}
