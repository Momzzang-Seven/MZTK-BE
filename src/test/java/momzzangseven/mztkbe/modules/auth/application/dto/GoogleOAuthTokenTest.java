package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GoogleOAuthToken unit test")
class GoogleOAuthTokenTest {

  @Test
  @DisplayName("of() creates token pair")
  void of_createsTokenPair() {
    GoogleOAuthToken token = GoogleOAuthToken.of("access-token", "refresh-token");

    assertThat(token.accessToken()).isEqualTo("access-token");
    assertThat(token.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  @DisplayName("of() allows null refresh token")
  void of_allowsNullRefreshToken() {
    GoogleOAuthToken token = GoogleOAuthToken.of("access-token", null);

    assertThat(token.accessToken()).isEqualTo("access-token");
    assertThat(token.refreshToken()).isNull();
  }
}
