package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.security.SensitiveTokenCipher;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * Executes provider-specific external disconnect.
 *
 * <p>KAKAO: unlink by provider user id (admin key)
 *
 * <p>GOOGLE: revoke refresh token (preferred)
 */
@Component
@RequiredArgsConstructor
public class ExternalDisconnectExecutor {

  private final KakaoAuthPort kakaoAuthPort;
  private final GoogleAuthPort googleAuthPort;
  private final SensitiveTokenCipher sensitiveTokenCipher;

  /**
   * Disconnect a social provider account.
   *
   * @param provider auth provider
   * @param providerUserId provider user id
   * @param encryptedGoogleToken encrypted Google refresh token (GOOGLE only)
   */
  public void disconnect(
      AuthProvider provider, String providerUserId, String encryptedGoogleToken) {
    if (provider == null) {
      throw new IllegalStateException("provider is missing");
    }
    if (provider == AuthProvider.KAKAO) {
      kakaoAuthPort.unlinkUser(providerUserId);
      return;
    }
    if (provider == AuthProvider.GOOGLE) {
      if (encryptedGoogleToken == null || encryptedGoogleToken.isBlank()) {
        throw new IllegalStateException("encrypted Google refresh token is missing");
      }
      String refreshToken = sensitiveTokenCipher.decrypt(encryptedGoogleToken);
      googleAuthPort.revokeRefreshToken(refreshToken);
      return;
    }

    throw new IllegalArgumentException("Unsupported provider for disconnect: " + provider);
  }
}
