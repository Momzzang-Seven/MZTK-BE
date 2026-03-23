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
  @DisplayName("of() creates command with imageIds")
  void of_createsCommand() {
    CreatePostCommand command =
        CreatePostCommand.of(1L, "title", "content", PostType.FREE, 0L, List.of(1L, 2L), List.of());

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.title()).isNull();
    assertThat(command.content()).isEqualTo("content");
    assertThat(command.type()).isEqualTo(PostType.FREE);
    assertThat(command.imageIds()).containsExactly(1L, 2L);
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
  @DisplayName("QUESTION post requires positive reward")
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

  @Test
  @DisplayName("validate rejects null content")
  void validate_nullContent_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, null, null, PostType.FREE, 0L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Content is required");
  }

  @Test
  @DisplayName("validate rejects FREE post with non-null title")
  void validate_freeBoardWithTitle_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, "title", "content", PostType.FREE, 0L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Title must be null for free board");
  }

  @Test
  @DisplayName("validate rejects FREE post with non-zero reward")
  void validate_freeBoardWithNonZeroReward_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, null, "content", PostType.FREE, 10L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Free posts must have zero reward");
  }

  @Test
  @DisplayName("of() with QUESTION type preserves title")
  void of_questionType_keepsTitleInCommand() {
    CreatePostCommand command =
        CreatePostCommand.of(
            1L, "question title", "content", PostType.QUESTION, 10L, List.of(1L), List.of());

    assertThat(command.title()).isEqualTo("question title");
    assertThat(command.type()).isEqualTo(PostType.QUESTION);
    assertThat(command.imageIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("validate rejects QUESTION with null title")
  void validate_questionWithNullTitle_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, null, "content", PostType.QUESTION, 10L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Title is required for question board");
  }

  @Test
  @DisplayName("validate rejects QUESTION with zero reward")
  void validate_questionWithZeroReward_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, "title", "content", PostType.QUESTION, 0L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Questions must have a valid reward");
  }

  @Test
  @DisplayName("validate rejects QUESTION with negative reward")
  void validate_questionWithNegativeReward_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(1L, "title", "content", PostType.QUESTION, -1L, List.of(), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Questions must have a valid reward");
  }

  @Test
  @DisplayName("validate rejects duplicate imageIds on create")
  void validate_duplicateImageIds_throwsException() {
    CreatePostCommand command =
        new CreatePostCommand(
            1L, null, "content", PostType.FREE, 0L, List.of(1L, 1L), List.of());

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Duplicate image IDs are not allowed");
  }
}
