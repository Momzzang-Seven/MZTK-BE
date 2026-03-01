package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdatePostCommand unit test")
class UpdatePostCommandTest {

  @Test
  @DisplayName("of() creates command")
  void of_createsCommand() {
    UpdatePostCommand command =
        UpdatePostCommand.of("new title", null, List.of("img1"), List.of("tag1"));

    assertThat(command.title()).isEqualTo("new title");
    assertThat(command.content()).isNull();
    assertThat(command.imageUrls()).containsExactly("img1");
    assertThat(command.tags()).containsExactly("tag1");
  }

  @Test
  @DisplayName("validate rejects blank title")
  void validate_blankTitle_throwsException() {
    UpdatePostCommand command = new UpdatePostCommand(" ", "content", null, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("수정할 제목은 비워둘 수 없습니다.");
  }

  @Test
  @DisplayName("validate rejects blank content")
  void validate_blankContent_throwsException() {
    UpdatePostCommand command = new UpdatePostCommand("title", " ", null, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("수정할 내용은 비워둘 수 없습니다.");
  }

  @Test
  @DisplayName("validate rejects fully empty patch")
  void validate_allFieldsNull_throwsException() {
    UpdatePostCommand command = new UpdatePostCommand(null, null, null, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("수정할 값이 없습니다.");
  }

  @Test
  @DisplayName("validate accepts partial update")
  void validate_partialUpdate_doesNotThrow() {
    UpdatePostCommand command = new UpdatePostCommand(null, "new content", null, null);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate accepts image-only update")
  void validate_imageOnlyUpdate_doesNotThrow() {
    UpdatePostCommand command = new UpdatePostCommand(null, null, List.of("img1"), null);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate accepts tags-only update")
  void validate_tagsOnlyUpdate_doesNotThrow() {
    UpdatePostCommand command = new UpdatePostCommand(null, null, null, List.of("java"));

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
