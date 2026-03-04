package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreatePostCommand unit test")
class CreatePostCommandTest {

  @Test
  @DisplayName("of() creates command")
  void of_createsCommand() {
    CreatePostCommand command =
        CreatePostCommand.of(1L, "title", "content", PostType.FREE, 0L, List.of(), List.of());

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.title()).isNull();
    assertThat(command.content()).isEqualTo("content");
    assertThat(command.type()).isEqualTo(PostType.FREE);
  }

  @Test
  @DisplayName("validate rejects blank title")
  void validate_blankTitle_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, " ", "content", PostType.QUESTION, 1L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Title is required");
  }

  @Test
  @DisplayName("validate rejects blank content")
  void validate_blankContent_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, null, " ", PostType.FREE, 0L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Content is required");
  }

  @Test
  @DisplayName("QUESTION post requires non-negative reward")
  void validate_questionWithoutReward_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(
            1L, "title", "content", PostType.QUESTION, null, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Questions must have a valid reward");
  }

  @Test
  @DisplayName("FREE post can omit reward")
  void validate_freeWithZeroReward_doesNotThrow() {
    CreatePostCommand command =
        new CreatePostCommand(1L, null, "content", PostType.FREE, 0L, List.of(), List.of());

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
