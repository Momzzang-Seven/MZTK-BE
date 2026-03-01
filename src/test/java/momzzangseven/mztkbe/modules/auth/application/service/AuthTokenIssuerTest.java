package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthTokenIssuer unit test")
class AuthTokenIssuerTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenManager refreshTokenManager;

  @InjectMocks private AuthTokenIssuer authTokenIssuer;

  @Test
  @DisplayName("issue() returns LoginResult with generated tokens")
  void issue_returnsLoginResultWithGeneratedTokens() {
    User user = sampleUser();

    given(jwtTokenProvider.generateAccessToken(1L, "user@example.com", UserRole.USER))
        .willReturn("access-token");
    given(refreshTokenManager.createAndSaveRefreshToken(1L)).willReturn("refresh-token");
    given(jwtTokenProvider.getAccessTokenExpiresIn()).willReturn(900L);
    given(jwtTokenProvider.getRefreshTokenExpiresIn()).willReturn(3600L);

    LoginResult result = authTokenIssuer.issue(user, true);

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
    assertThat(result.refreshTokenExpiresIn()).isEqualTo(3600L);
    assertThat(result.isNewUser()).isTrue();
    assertThat(result.user()).isSameAs(user);

    verify(jwtTokenProvider).generateAccessToken(1L, "user@example.com", UserRole.USER);
    verify(refreshTokenManager).createAndSaveRefreshToken(1L);
  }

  @Test
  @DisplayName("issue() with null user throws NullPointerException")
  void issue_withNullUser_throwsNullPointerException() {
    assertThatThrownBy(() -> authTokenIssuer.issue(null, false))
        .isInstanceOf(NullPointerException.class);

    verifyNoInteractions(jwtTokenProvider, refreshTokenManager);
  }

  private User sampleUser() {
    return User.builder()
        .id(1L)
        .email("user@example.com")
        .role(UserRole.USER)
        .authProvider(AuthProvider.LOCAL)
        .build();
  }
}
