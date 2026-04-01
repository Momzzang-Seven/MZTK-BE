package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginResult unit test")
class LoginResultTest {

  @Test
  @DisplayName("of() sets Bearer grant type and maps values")
  void of_setsBearerAndMapsValues() {
    User user = sampleUser();

    LoginResult result = LoginResult.of("access", "refresh", 900L, 3600L, false, user, "0xabc");

    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.refreshToken()).isEqualTo("refresh");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
    assertThat(result.refreshTokenExpiresIn()).isEqualTo(3600L);
    assertThat(result.isNewUser()).isFalse();
    assertThat(result.user()).isSameAs(user);
    assertThat(result.walletAddress()).isEqualTo("0xabc");
  }

  @Test
  @DisplayName("of() keeps null token values as-is")
  void of_keepsNullTokenValues() {
    LoginResult result = LoginResult.of(null, null, 1L, 2L, true, null, null);

    assertThat(result.accessToken()).isNull();
    assertThat(result.refreshToken()).isNull();
    assertThat(result.isNewUser()).isTrue();
    assertThat(result.user()).isNull();
  }

  private User sampleUser() {
    return User.builder()
        .id(1L)
        .email("user@example.com")
        .authProvider(AuthProvider.LOCAL)
        .role(UserRole.USER)
        .build();
  }
}
