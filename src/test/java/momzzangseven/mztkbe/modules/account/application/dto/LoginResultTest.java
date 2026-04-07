package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginResult unit test")
class LoginResultTest {

  @Test
  @DisplayName("of() sets Bearer grant type and maps values")
  void of_setsBearerAndMapsValues() {
    AccountUserSnapshot snapshot = sampleSnapshot();

    LoginResult result = LoginResult.of("access", "refresh", 900L, 3600L, false, snapshot, "0xabc");

    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.refreshToken()).isEqualTo("refresh");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
    assertThat(result.refreshTokenExpiresIn()).isEqualTo(3600L);
    assertThat(result.isNewUser()).isFalse();
    assertThat(result.userSnapshot()).isSameAs(snapshot);
    assertThat(result.walletAddress()).isEqualTo("0xabc");
  }

  @Test
  @DisplayName("of() keeps null token values as-is")
  void of_keepsNullTokenValues() {
    LoginResult result = LoginResult.of(null, null, 1L, 2L, true, null, null);

    assertThat(result.accessToken()).isNull();
    assertThat(result.refreshToken()).isNull();
    assertThat(result.isNewUser()).isTrue();
    assertThat(result.userSnapshot()).isNull();
  }

  private AccountUserSnapshot sampleSnapshot() {
    return new AccountUserSnapshot(1L, "user@example.com", "tester", null, "USER");
  }
}
