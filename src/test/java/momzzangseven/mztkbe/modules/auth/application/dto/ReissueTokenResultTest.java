package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReissueTokenResult unit test")
class ReissueTokenResultTest {

  @Test
  @DisplayName("of() sets default grantType to Bearer")
  void of_setsDefaultGrantType() {
    ReissueTokenResult result = ReissueTokenResult.of("access", "refresh", 900L, 3600L);

    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.refreshToken()).isEqualTo("refresh");
  }

  @Test
  @DisplayName("validate rejects blank access token")
  void validate_blankAccessToken_throwsException() {
    ReissueTokenResult result = new ReissueTokenResult(" ", "refresh", "Bearer", 900L, 3600L);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Access token cannot be empty");
  }

  @Test
  @DisplayName("validate rejects blank refresh token")
  void validate_blankRefreshToken_throwsException() {
    ReissueTokenResult result = new ReissueTokenResult("access", " ", "Bearer", 900L, 3600L);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Refresh token cannot be empty");
  }

  @Test
  @DisplayName("validate rejects non-positive expiresIn")
  void validate_nonPositiveExpiresIn_throwsException() {
    ReissueTokenResult result = new ReissueTokenResult("access", "refresh", "Bearer", 0L, 3600L);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ExpiresIn must be positive");
  }

  @Test
  @DisplayName("validate passes with valid values")
  void validate_validResult_doesNotThrow() {
    ReissueTokenResult result = new ReissueTokenResult("access", "refresh", "Bearer", 900L, 3600L);

    assertThatCode(result::validate).doesNotThrowAnyException();
  }
}
