package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.security.SensitiveTokenCipher;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalDisconnectExecutor unit test")
class ExternalDisconnectExecutorTest {

  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private SensitiveTokenCipher sensitiveTokenCipher;

  @Test
  @DisplayName("disconnect throws when provider is null")
  void disconnect_withNullProvider_throwsIllegalStateException() {
    ExternalDisconnectExecutor executor =
        new ExternalDisconnectExecutor(kakaoAuthPort, googleAuthPort, sensitiveTokenCipher);

    assertThatThrownBy(() -> executor.disconnect(null, "provider-id", "encrypted"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider is missing");

    verify(kakaoAuthPort, never()).unlinkUser(org.mockito.ArgumentMatchers.anyString());
    verify(googleAuthPort, never()).revokeRefreshToken(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("disconnect delegates kakao unlink")
  void disconnect_withKakao_callsUnlink() {
    ExternalDisconnectExecutor executor =
        new ExternalDisconnectExecutor(kakaoAuthPort, googleAuthPort, sensitiveTokenCipher);

    executor.disconnect(AuthProvider.KAKAO, "kakao-1", null);

    verify(kakaoAuthPort).unlinkUser("kakao-1");
    verify(sensitiveTokenCipher, never()).decrypt(org.mockito.ArgumentMatchers.anyString());
    verify(googleAuthPort, never()).revokeRefreshToken(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("disconnect throws when google token is missing")
  void disconnect_withGoogleAndBlankToken_throwsIllegalStateException() {
    ExternalDisconnectExecutor executor =
        new ExternalDisconnectExecutor(kakaoAuthPort, googleAuthPort, sensitiveTokenCipher);

    assertThatThrownBy(() -> executor.disconnect(AuthProvider.GOOGLE, "google-1", " "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("encrypted Google refresh token is missing");
  }

  @Test
  @DisplayName("disconnect decrypts and revokes google refresh token")
  void disconnect_withGoogle_decryptsAndRevokes() {
    ExternalDisconnectExecutor executor =
        new ExternalDisconnectExecutor(kakaoAuthPort, googleAuthPort, sensitiveTokenCipher);
    when(sensitiveTokenCipher.decrypt("encrypted")).thenReturn("refresh-token");

    executor.disconnect(AuthProvider.GOOGLE, "google-1", "encrypted");

    verify(sensitiveTokenCipher).decrypt("encrypted");
    verify(googleAuthPort).revokeRefreshToken("refresh-token");
    verify(kakaoAuthPort, never()).unlinkUser(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("disconnect rejects unsupported provider")
  void disconnect_withUnsupportedProvider_throwsIllegalArgumentException() {
    ExternalDisconnectExecutor executor =
        new ExternalDisconnectExecutor(kakaoAuthPort, googleAuthPort, sensitiveTokenCipher);

    assertThatThrownBy(() -> executor.disconnect(AuthProvider.LOCAL, "local-1", "encrypted"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported provider for disconnect");
  }
}
