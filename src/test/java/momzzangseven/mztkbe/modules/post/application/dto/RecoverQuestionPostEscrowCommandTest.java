package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecoverQuestionPostEscrowCommand unit test")
class RecoverQuestionPostEscrowCommandTest {

  @Test
  @DisplayName("validate rejects invalid requesterId")
  void validate_rejectsInvalidRequesterId() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(0L, 10L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("requesterId must be positive");
  }

  @Test
  @DisplayName("validate rejects invalid postId")
  void validate_rejectsInvalidPostId() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(1L, 0L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("postId must be positive");
  }

  @Test
  @DisplayName("validate accepts valid input")
  void validate_acceptsValidInput() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(1L, 10L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate accepts null optional mutation fields")
  void validate_acceptsNullOptionalMutationFields() {
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(1L, 10L, null, null, null, null);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate accepts empty tags and imageIds")
  void validate_acceptsEmptyLists() {
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(1L, 10L, null, null, List.of(), List.of());

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate rejects duplicate imageIds")
  void validate_rejectsDuplicateImageIds() {
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(1L, 10L, null, null, List.of(1L, 1L), null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Duplicate image IDs");
  }

  @Test
  @DisplayName("validate rejects null and non-positive imageIds")
  void validate_rejectsNullAndNonPositiveImageIds() {
    assertThatThrownBy(
            () ->
                new RecoverQuestionPostEscrowCommand(1L, 10L, null, null, List.of(0L), null)
                    .validate())
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Image IDs must be positive");

    assertThatThrownBy(
            () ->
                new RecoverQuestionPostEscrowCommand(
                        1L, 10L, null, null, Collections.singletonList(null), null)
                    .validate())
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Image IDs must be positive");
  }

  @Test
  @DisplayName("validate rejects blank title, content, and tags")
  void validate_rejectsBlankMutationFields() {
    assertThatThrownBy(
            () -> new RecoverQuestionPostEscrowCommand(1L, 10L, " ", null, null, null).validate())
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Title cannot be blank");

    assertThatThrownBy(
            () -> new RecoverQuestionPostEscrowCommand(1L, 10L, null, " ", null, null).validate())
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Content cannot be blank");

    assertThatThrownBy(
            () ->
                new RecoverQuestionPostEscrowCommand(1L, 10L, null, null, null, List.of("tag", " "))
                    .validate())
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Tag cannot be blank");
  }

  @Test
  @DisplayName("hasMutationFields treats empty lists as explicit mutation fields")
  void hasMutationFields_emptyLists_returnsTrue() {
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(1L, 10L, null, null, List.of(), List.of());

    assertThat(command.hasMutationFields()).isTrue();
  }
}
