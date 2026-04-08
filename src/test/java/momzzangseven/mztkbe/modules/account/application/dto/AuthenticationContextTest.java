package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticationContext unit test")
class AuthenticationContextTest {

  @Test
  @DisplayName("from() maps all fields from LoginCommand")
  void from_mapsAllFields() {
    LoginCommand command =
        new LoginCommand(
            AuthProvider.GOOGLE, "user@example.com", "pw", "auth-code", "redirect", "USER");

    AuthenticationContext context = AuthenticationContext.from(command);

    assertThat(context.provider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(context.email()).isEqualTo("user@example.com");
    assertThat(context.password()).isEqualTo("pw");
    assertThat(context.authorizationCode()).isEqualTo("auth-code");
    assertThat(context.redirectUri()).isEqualTo("redirect");
    assertThat(context.role()).isEqualTo("USER");
  }

  @Test
  @DisplayName("[M-17] from() maps null role from LoginCommand")
  void from_mapsNullRole() {
    LoginCommand command =
        new LoginCommand(AuthProvider.KAKAO, null, null, "auth-code", "redirect", null);

    AuthenticationContext context = AuthenticationContext.from(command);

    assertThat(context.role()).isNull();
  }

  @Test
  @DisplayName("isValidForLocal returns true only when email and password are non-blank")
  void isValidForLocal_checksNonBlankEmailAndPassword() {
    AuthenticationContext valid =
        new AuthenticationContext(
            AuthProvider.LOCAL, "user@example.com", "password", null, null, null);
    AuthenticationContext blankEmail =
        new AuthenticationContext(AuthProvider.LOCAL, " ", "password", null, null, null);
    AuthenticationContext blankPassword =
        new AuthenticationContext(AuthProvider.LOCAL, "user@example.com", " ", null, null, null);

    assertThat(valid.isValidForLocal()).isTrue();
    assertThat(blankEmail.isValidForLocal()).isFalse();
    assertThat(blankPassword.isValidForLocal()).isFalse();
  }

  @Test
  @DisplayName("isValidForSocial returns true only when authorization code is non-blank")
  void isValidForSocial_checksAuthorizationCode() {
    AuthenticationContext valid =
        new AuthenticationContext(AuthProvider.KAKAO, null, null, "code", null, null);
    AuthenticationContext blank =
        new AuthenticationContext(AuthProvider.KAKAO, null, null, "   ", null, null);

    assertThat(valid.isValidForSocial()).isTrue();
    assertThat(blank.isValidForSocial()).isFalse();
  }
}
