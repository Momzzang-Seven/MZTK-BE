package momzzangseven.mztkbe.modules.auth.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("AuthProvider 열거형 단위 테스트")
class AuthProviderTest {

  @Nested
  @DisplayName("isSocialLogin()")
  class IsSocialLoginTest {

    @Test
    @DisplayName("LOCAL은 소셜 로그인이 아님")
    void local_IsNotSocialLogin() {
      assertThat(AuthProvider.LOCAL.isSocialLogin()).isFalse();
    }

    @Test
    @DisplayName("KAKAO는 소셜 로그인")
    void kakao_IsSocialLogin() {
      assertThat(AuthProvider.KAKAO.isSocialLogin()).isTrue();
    }

    @Test
    @DisplayName("GOOGLE은 소셜 로그인")
    void google_IsSocialLogin() {
      assertThat(AuthProvider.GOOGLE.isSocialLogin()).isTrue();
    }
  }

  @Nested
  @DisplayName("requiresOAuth()")
  class RequiresOAuthTest {

    @Test
    @DisplayName("LOCAL은 OAuth 불필요")
    void local_DoesNotRequireOAuth() {
      assertThat(AuthProvider.LOCAL.requiresOAuth()).isFalse();
    }

    @ParameterizedTest(name = "{0}는 OAuth 필요")
    @EnumSource(
        value = AuthProvider.class,
        names = {"KAKAO", "GOOGLE"})
    @DisplayName("소셜 로그인 provider는 OAuth 필요")
    void social_RequiresOAuth(AuthProvider provider) {
      assertThat(provider.requiresOAuth()).isTrue();
    }
  }

  @Nested
  @DisplayName("requiresCredentials()")
  class RequiresCredentialsTest {

    @Test
    @DisplayName("LOCAL은 이메일/비밀번호 필요")
    void local_RequiresCredentials() {
      assertThat(AuthProvider.LOCAL.requiresCredentials()).isTrue();
    }

    @ParameterizedTest(name = "{0}은 이메일/비밀번호 불필요")
    @EnumSource(
        value = AuthProvider.class,
        names = {"KAKAO", "GOOGLE"})
    @DisplayName("소셜 provider는 이메일/비밀번호 불필요")
    void social_DoesNotRequireCredentials(AuthProvider provider) {
      assertThat(provider.requiresCredentials()).isFalse();
    }
  }

  @Nested
  @DisplayName("비즈니스 규칙 일관성 검증")
  class ConsistencyTest {

    @Test
    @DisplayName("isSocialLogin == requiresOAuth: 두 메서드는 동일한 결과 반환")
    void isSocialLogin_EqualsRequiresOAuth_ForAllProviders() {
      for (AuthProvider provider : AuthProvider.values()) {
        assertThat(provider.isSocialLogin())
            .as("provider=%s: isSocialLogin should equal requiresOAuth", provider)
            .isEqualTo(provider.requiresOAuth());
      }
    }

    @Test
    @DisplayName("isSocialLogin과 requiresCredentials는 서로 배타적")
    void isSocialLogin_AndRequiresCredentials_AreMutuallyExclusive() {
      for (AuthProvider provider : AuthProvider.values()) {
        assertThat(provider.isSocialLogin() && provider.requiresCredentials())
            .as("provider=%s: cannot be both social and credentials", provider)
            .isFalse();
      }
    }

    @Test
    @DisplayName("모든 provider는 displayName과 description을 보유")
    void allProviders_HaveDisplayNameAndDescription() {
      for (AuthProvider provider : AuthProvider.values()) {
        assertThat(provider.getDisplayName()).isNotBlank();
        assertThat(provider.getDescription()).isNotBlank();
      }
    }
  }
}
