package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.*;

import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LoginCommand 단위 테스트")
class LoginCommandTest {

  // ============================================
  // LOCAL 로그인 검증
  // ============================================

  @Nested
  @DisplayName("LOCAL 로그인 validate()")
  class LocalLoginValidationTest {

    @Test
    @DisplayName("이메일 + 비밀번호 정상 입력 시 예외 없음")
    void validate_ValidLocalCommand_NoException() {
      LoginCommand command =
          new LoginCommand(AuthProvider.LOCAL, "user@example.com", "password123", null, null);

      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LOCAL 로그인 시 이메일 없으면 예외 발생")
    void validate_LocalMissingEmail_ThrowsException() {
      LoginCommand command = new LoginCommand(AuthProvider.LOCAL, null, "password123", null, null);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email is required for LOCAL login");
    }

    @Test
    @DisplayName("LOCAL 로그인 시 이메일이 빈 문자열이면 예외 발생")
    void validate_LocalBlankEmail_ThrowsException() {
      LoginCommand command = new LoginCommand(AuthProvider.LOCAL, "   ", "password123", null, null);

      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("LOCAL 로그인 시 비밀번호 없으면 예외 발생")
    void validate_LocalMissingPassword_ThrowsException() {
      LoginCommand command =
          new LoginCommand(AuthProvider.LOCAL, "user@example.com", null, null, null);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Password is required for LOCAL login");
    }

    @Test
    @DisplayName("LOCAL 로그인 시 비밀번호가 빈 문자열이면 예외 발생")
    void validate_LocalBlankPassword_ThrowsException() {
      LoginCommand command =
          new LoginCommand(AuthProvider.LOCAL, "user@example.com", "  ", null, null);

      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ============================================
  // 소셜 로그인 검증 (KAKAO / GOOGLE)
  // ============================================

  @Nested
  @DisplayName("소셜 로그인 validate() - KAKAO")
  class KakaoLoginValidationTest {

    @Test
    @DisplayName("authorizationCode 있으면 예외 없음")
    void validate_ValidKakaoCommand_NoException() {
      LoginCommand command =
          new LoginCommand(AuthProvider.KAKAO, null, null, "valid-auth-code", null);

      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("KAKAO 로그인 시 authorizationCode 없으면 예외 발생")
    void validate_KakaoMissingCode_ThrowsException() {
      LoginCommand command = new LoginCommand(AuthProvider.KAKAO, null, null, null, null);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Authorization code is required");
    }

    @Test
    @DisplayName("KAKAO 로그인 시 authorizationCode가 빈 문자열이면 예외 발생")
    void validate_KakaoBlankCode_ThrowsException() {
      LoginCommand command = new LoginCommand(AuthProvider.KAKAO, null, null, "  ", null);

      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("소셜 로그인 validate() - GOOGLE")
  class GoogleLoginValidationTest {

    @Test
    @DisplayName("authorizationCode 있으면 예외 없음")
    void validate_ValidGoogleCommand_NoException() {
      LoginCommand command =
          new LoginCommand(AuthProvider.GOOGLE, null, null, "google-auth-code", null);

      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("GOOGLE 로그인 시 authorizationCode 없으면 예외 발생")
    void validate_GoogleMissingCode_ThrowsException() {
      LoginCommand command = new LoginCommand(AuthProvider.GOOGLE, null, null, null, null);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Authorization code is required");
    }
  }

  // ============================================
  // provider null 검증
  // ============================================

  @Nested
  @DisplayName("provider null 검증")
  class ProviderValidationTest {

    @Test
    @DisplayName("provider가 null이면 예외 발생")
    void validate_NullProvider_ThrowsException() {
      LoginCommand command = new LoginCommand(null, "user@example.com", "pw", null, null);

      assertThatThrownBy(command::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Provider is required");
    }
  }
}
