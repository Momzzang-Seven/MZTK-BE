package momzzangseven.mztkbe.modules.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateUserRoleCommand 단위 테스트")
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

  // ══════════════════════════════════════════════════════════════
  // Commit 1 — MOM-330: validate() — isAdmin()-based admin guard
  // ══════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("validate — isAdmin() 기반 관리자 차단 검증")
  class ValidateAdminGuard {

    @Test
    @DisplayName("[M-39] validate rejects ADMIN_SEED")
    void validate_withAdminSeed_throwsIllegalAdminGrantException() {
      // given
      UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.ADMIN_SEED);

      // when / then
      assertThatThrownBy(command::validate).isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-40] validate rejects ADMIN_GENERATED")
    void validate_withAdminGenerated_throwsIllegalAdminGrantException() {
      // given
      UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.ADMIN_GENERATED);

      // when / then
      assertThatThrownBy(command::validate).isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-41] validate still rejects plain ADMIN (existing behavior preserved)")
    void validate_withAdmin_throwsIllegalAdminGrantException() {
      // given
      UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.ADMIN);

      // when / then
      assertThatThrownBy(command::validate).isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-42] validate passes for USER role")
    void validate_withUser_doesNotThrow() {
      // given
      UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.USER);

      // when / then
      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[M-43] validate passes for TRAINER role")
    void validate_withTrainer_doesNotThrow() {
      // given
      UpdateUserRoleCommand command = new UpdateUserRoleCommand(1L, UserRole.TRAINER);

      // when / then
      assertThatCode(command::validate).doesNotThrowAnyException();
    }
  }
}
