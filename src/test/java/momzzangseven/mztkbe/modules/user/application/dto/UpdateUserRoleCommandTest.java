package momzzangseven.mztkbe.modules.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateUserRoleCommand unit test")
class UpdateUserRoleCommandTest {

  @Test
  @DisplayName("of() creates command")
  void of_createsCommand() {
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(1L, UserRole.TRAINER);

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.newRole()).isEqualTo(UserRole.TRAINER);
  }

  @Test
  @DisplayName("validate rejects non-positive user ID")
  void validate_nonPositiveUserId_throwsException() {
    UpdateUserRoleCommand command = new UpdateUserRoleCommand(0L, UserRole.TRAINER);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID is required");
  }

  @Test
  @DisplayName("validate rejects null user ID")
  void validate_nullUserId_throwsException() {
    UpdateUserRoleCommand command = new UpdateUserRoleCommand(null, UserRole.TRAINER);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID is required");
  }

  @Test
  @DisplayName("validate rejects null role")
  void validate_nullRole_throwsException() {
    UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidUserRoleException.class)
        .hasMessageContaining("Cannot update user role if no role is provided");
  }

  @Test
  @DisplayName("validate rejects ADMIN grant")
  void validate_adminRole_throwsException() {
    UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.ADMIN);

    assertThatThrownBy(command::validate).isInstanceOf(IllegalAdminGrantException.class);
  }

  @Test
  @DisplayName("validate passes for non-admin role")
  void validate_validCommand_doesNotThrow() {
    UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.TRAINER);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
