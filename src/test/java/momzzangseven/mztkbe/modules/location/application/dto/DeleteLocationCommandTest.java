package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeleteLocationCommand unit test")
class DeleteLocationCommandTest {

  @Test
  @DisplayName("of() creates command with user and location IDs")
  void of_createsCommand() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, 2L);

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.locationId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("validate rejects null userId")
  void validate_nullUserId_throwsException() {
    DeleteLocationCommand command = new DeleteLocationCommand(null, 2L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId is required");
  }

  @Test
  @DisplayName("validate rejects null locationId")
  void validate_nullLocationId_throwsException() {
    DeleteLocationCommand command = new DeleteLocationCommand(1L, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(MissingLocationInfoException.class)
        .hasMessageContaining("locationId is required");
  }

  @Test
  @DisplayName("validate passes when both IDs are present")
  void validate_validCommand_doesNotThrow() {
    DeleteLocationCommand command = new DeleteLocationCommand(1L, 2L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
