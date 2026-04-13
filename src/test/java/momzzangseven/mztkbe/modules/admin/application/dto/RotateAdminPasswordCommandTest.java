package momzzangseven.mztkbe.modules.admin.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RotateAdminPasswordCommand 단위 테스트")
class RotateAdminPasswordCommandTest {

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-150] validate succeeds with all fields present")
    void validate_allFieldsPresent_succeeds() {
      // given
      RotateAdminPasswordCommand command = new RotateAdminPasswordCommand(1L, "current", "newPass");

      // when & then
      assertThatNoException().isThrownBy(command::validate);
    }
  }

  @Nested
  @DisplayName("userId 검증")
  class UserIdValidation {

    @Test
    @DisplayName("[M-151] validate throws when userId is null")
    void validate_nullUserId_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(null, "current", "newPass");

      // when & then
      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must not be null");
    }
  }

  @Nested
  @DisplayName("currentPassword 검증")
  class CurrentPasswordValidation {

    @Test
    @DisplayName("[M-152] validate throws when currentPassword is null")
    void validate_nullCurrentPassword_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command = new RotateAdminPasswordCommand(1L, null, "newPass");

      // when & then
      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Current password must not be blank");
    }

    @Test
    @DisplayName("[M-153] validate throws when currentPassword is blank")
    void validate_blankCurrentPassword_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command = new RotateAdminPasswordCommand(1L, "   ", "newPass");

      // when & then
      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Current password must not be blank");
    }
  }

  @Nested
  @DisplayName("newPassword 검증")
  class NewPasswordValidation {

    @Test
    @DisplayName("[M-154] validate throws when newPassword is null")
    void validate_nullNewPassword_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command = new RotateAdminPasswordCommand(1L, "current", null);

      // when & then
      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("New password must not be blank");
    }

    @Test
    @DisplayName("[M-155] validate throws when newPassword is blank")
    void validate_blankNewPassword_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command = new RotateAdminPasswordCommand(1L, "current", "   ");

      // when & then
      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("New password must not be blank");
    }
  }
}
