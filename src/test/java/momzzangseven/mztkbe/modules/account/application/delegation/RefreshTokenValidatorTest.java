package momzzangseven.mztkbe.modules.account.application.delegation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.token.RefreshTokenInvalidException;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.global.error.token.TokenSecurityException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenValidatorTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private LoadRefreshTokenPort loadRefreshTokenPort;
  @Mock private SaveRefreshTokenPort saveRefreshTokenPort;
  @Mock private RefreshTokenManager refreshTokenManager;

  private RefreshTokenValidator validator;

  @BeforeEach
  void setUp() {
    validator =
        new RefreshTokenValidator(
            jwtTokenProvider, loadRefreshTokenPort, saveRefreshTokenPort, refreshTokenManager);
  }

  @Test
  void validateJwtFormat_throws_whenTokenSignatureInvalid() {
    when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

    assertThatThrownBy(() -> validator.validateJwtFormat("bad-token"))
        .isInstanceOf(RefreshTokenNotFoundException.class);
  }

  @Test
  void validateJwtFormat_throws_whenTokenIsNotRefreshType() {
    when(jwtTokenProvider.validateToken("token")).thenReturn(true);
    when(jwtTokenProvider.isRefreshToken("token")).thenReturn(false);

    assertThatThrownBy(() -> validator.validateJwtFormat("token"))
        .isInstanceOf(RefreshTokenNotFoundException.class);
  }

  @Test
  void validateJwtFormat_passes_whenTokenValidAndRefreshType() {
    when(jwtTokenProvider.validateToken("token")).thenReturn(true);
    when(jwtTokenProvider.isRefreshToken("token")).thenReturn(true);

    validator.validateJwtFormat("token");
  }

  @Test
  void loadTokenByValueWithLock_throws_whenTokenMissingInDatabase() {
    when(loadRefreshTokenPort.findByTokenValueWithLock("token")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> validator.loadTokenByValueWithLock("token"))
        .isInstanceOf(RefreshTokenNotFoundException.class)
        .hasMessageContaining("Refresh token not found in database");
  }

  @Test
  void validateUserIdConsistency_revokesAndThrows_whenUserIdMismatch() {
    RefreshToken token = validToken(99L);

    assertThatThrownBy(() -> validator.validateUserIdConsistency(1L, token))
        .isInstanceOf(TokenSecurityException.class);
    verify(refreshTokenManager).revokeToken(token);
  }

  @Test
  void validateDomainRules_throwsExpired_whenTokenExpired() {
    RefreshToken expiredToken =
        RefreshToken.builder()
            .id(1L)
            .userId(1L)
            .tokenValue("refresh-token-12345")
            .createdAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();

    assertThatThrownBy(() -> validator.validateDomainRules(expiredToken))
        .isInstanceOf(RefreshTokenInvalidException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void validateDomainRules_throwsRevoked_whenTokenRevoked() {
    RefreshToken revokedToken =
        RefreshToken.builder()
            .id(1L)
            .userId(1L)
            .tokenValue("refresh-token-12345")
            .createdAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusDays(1))
            .revokedAt(LocalDateTime.now().minusMinutes(1))
            .build();

    assertThatThrownBy(() -> validator.validateDomainRules(revokedToken))
        .isInstanceOf(RefreshTokenInvalidException.class)
        .hasMessageContaining("revoked");
  }

  @Test
  void checkTokenReuse_revokesAndThrows_whenTokenUsedRecently() {
    RefreshToken recentlyUsedToken =
        RefreshToken.builder()
            .id(1L)
            .userId(1L)
            .tokenValue("refresh-token-12345")
            .createdAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusDays(1))
            .usedAt(LocalDateTime.now().minusMinutes(1))
            .build();

    assertThatThrownBy(() -> validator.checkTokenReuse(recentlyUsedToken, 5))
        .isInstanceOf(TokenSecurityException.class)
        .hasMessageContaining("Token reuse detected");
    verify(refreshTokenManager).revokeToken(recentlyUsedToken);
  }

  @Test
  void markTokenUsed_updatesUsedAtAndPersistsToken() {
    RefreshToken token = validToken(1L);
    when(saveRefreshTokenPort.save(token)).thenReturn(token);

    validator.markTokenUsed(token);

    assertThat(token.getUsedAt()).isNotNull();
    verify(saveRefreshTokenPort).save(token);
  }

  @Test
  void inspectSecurityFlaw_runsAllChecksAndReturnsToken_whenTokenSafe() {
    RefreshToken token = validToken(7L);
    when(loadRefreshTokenPort.findByTokenValueWithLock("token-value"))
        .thenReturn(Optional.of(token));
    when(saveRefreshTokenPort.save(token)).thenReturn(token);

    RefreshToken result = validator.inspectSecurityFlaw("token-value", 7L);

    assertThat(result).isSameAs(token);
    verify(loadRefreshTokenPort).findByTokenValueWithLock("token-value");
    verify(saveRefreshTokenPort).save(token);
  }

  private RefreshToken validToken(Long userId) {
    return RefreshToken.builder()
        .id(1L)
        .userId(userId)
        .tokenValue("refresh-token-12345")
        .createdAt(LocalDateTime.now().minusDays(1))
        .expiresAt(LocalDateTime.now().plusDays(1))
        .build();
  }
}
