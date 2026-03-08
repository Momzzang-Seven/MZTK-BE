package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReissueTokenCommand unit test")
class ReissueTokenCommandTest {

  @Test
  @DisplayName("of() returns command for valid token")
  void of_validToken_returnsCommand() {
    String token = "a".repeat(10);

    ReissueTokenCommand command = ReissueTokenCommand.of(token);

    assertThat(command.refreshToken()).isEqualTo(token);
  }

  @Test
  @DisplayName("validate rejects null token")
  void validate_nullToken_throwsException() {
    ReissueTokenCommand command = new ReissueTokenCommand(null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(RefreshTokenNotFoundException.class)
        .hasMessageContaining("Refresh token is required");
  }

  @Test
  @DisplayName("validate rejects short token")
  void validate_shortToken_throwsException() {
    ReissueTokenCommand command = new ReissueTokenCommand("short");

    assertThatThrownBy(command::validate)
        .isInstanceOf(RefreshTokenNotFoundException.class)
        .hasMessageContaining("Invalid refresh token format");
  }

  @Test
  @DisplayName("validate rejects too long token")
  void validate_tooLongToken_throwsException() {
    ReissueTokenCommand command = new ReissueTokenCommand("a".repeat(501));

    assertThatThrownBy(command::validate)
        .isInstanceOf(RefreshTokenNotFoundException.class)
        .hasMessageContaining("Invalid refresh token format");
  }

  @Test
  @DisplayName("validate accepts boundary length token")
  void validate_boundaryToken_doesNotThrow() {
    ReissueTokenCommand command = new ReissueTokenCommand("a".repeat(10));

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
