package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReactivateCommand unit test")
class ReactivateCommandTest {

  @Test
  @DisplayName("LOCAL provider with email/password is valid")
  void validate_localValid_doesNotThrow() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "pw1234", null, null);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("LOCAL provider rejects authorizationCode")
  void validate_localWithAuthorizationCode_throwsException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "pw1234", "oauth-code", null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authorizationCode must not be provided");
  }

  @Test
  @DisplayName("SOCIAL provider requires authorizationCode")
  void validate_socialWithoutAuthorizationCode_throwsException() {
    ReactivateCommand command = new ReactivateCommand(AuthProvider.KAKAO, null, null, " ", null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Authorization code is required");
  }

  @Test
  @DisplayName("SOCIAL provider rejects password")
  void validate_socialWithPassword_throwsException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.GOOGLE, null, "pw1234", "code", null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("password must not be provided");
  }

  @Test
  @DisplayName("provider is required")
  void validate_nullProvider_throwsException() {
    ReactivateCommand command = new ReactivateCommand(null, null, null, null, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Provider is required");
  }
}
