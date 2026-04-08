package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SignupCommand 단위 테스트")
class SignupCommandTest {

  private static final String VALID_EMAIL = "user@example.com";
  private static final String VALID_PASSWORD = "Password123!";
  private static final String VALID_NICKNAME = "testuser";

  @Nested
  @DisplayName("validate() - 정상 입력")
  class ValidInputTest {

    @Test
    @DisplayName("정상 입력 시 예외 없음")
    void validate_ValidCommand_NoException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);

      assertThatCode(command::validate).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("validate() - 이메일 검증")
  class EmailValidationTest {

    @Test
    @DisplayName("이메일이 null이면 예외 발생")
    void validate_NullEmail_ThrowsException() {
      SignupCommand command = new SignupCommand(null, VALID_PASSWORD, VALID_NICKNAME);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email is required");
    }

    @Test
    @DisplayName("이메일이 빈 문자열이면 예외 발생")
    void validate_BlankEmail_ThrowsException() {
      SignupCommand command = new SignupCommand("   ", VALID_PASSWORD, VALID_NICKNAME);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email is required");
    }
  }

  @Nested
  @DisplayName("validate() - 비밀번호 검증")
  class PasswordValidationTest {

    @Test
    @DisplayName("비밀번호가 null이면 예외 발생")
    void validate_NullPassword_ThrowsException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, null, VALID_NICKNAME);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Password is required");
    }

    @Test
    @DisplayName("비밀번호가 빈 문자열이면 예외 발생")
    void validate_BlankPassword_ThrowsException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, "   ", VALID_NICKNAME);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Password is required");
    }
  }

  @Nested
  @DisplayName("validate() - 닉네임 검증")
  class NicknameValidationTest {

    @Test
    @DisplayName("닉네임이 null이면 예외 발생")
    void validate_NullNickname_ThrowsException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, null);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nickname is required");
    }

    @Test
    @DisplayName("닉네임이 빈 문자열이면 예외 발생")
    void validate_BlankNickname_ThrowsException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, "  ");

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nickname is required");
    }
  }
}
