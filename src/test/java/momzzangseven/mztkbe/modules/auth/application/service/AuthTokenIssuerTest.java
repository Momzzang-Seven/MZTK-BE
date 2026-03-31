package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.dto.IssuedTokens;
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
  @DisplayName("issueTokens() returns IssuedTokens with generated tokens")
  void issueTokens_returnsIssuedTokensWithGeneratedTokens() {
    given(jwtTokenProvider.generateAccessToken(1L, "user@example.com", UserRole.USER))
        .willReturn("access-token");
    given(refreshTokenManager.createAndSaveRefreshToken(1L)).willReturn("refresh-token");
    given(jwtTokenProvider.getAccessTokenExpiresIn()).willReturn(900L);
    given(jwtTokenProvider.getRefreshTokenExpiresIn()).willReturn(3600L);

    IssuedTokens result = authTokenIssuer.issueTokens(1L, "user@example.com", UserRole.USER);

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
    assertThat(result.refreshTokenExpiresIn()).isEqualTo(3600L);

    verify(jwtTokenProvider).generateAccessToken(1L, "user@example.com", UserRole.USER);
    verify(refreshTokenManager).createAndSaveRefreshToken(1L);
  }
}
