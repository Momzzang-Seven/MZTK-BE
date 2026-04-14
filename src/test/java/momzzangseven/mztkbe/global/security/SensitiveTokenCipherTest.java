package momzzangseven.mztkbe.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import momzzangseven.mztkbe.global.error.token.TokenEncryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensitiveTokenCipherTest {

  @Mock private AesGcmCipher aesGcmCipher;

  private JwtProperties jwtProperties;
  private SensitiveTokenCipher cipher;
  private SecretKey key;

  @BeforeEach
  void setUp() {
    jwtProperties = new JwtProperties();
    jwtProperties.setSecret("jwt-secret");
    cipher = new SensitiveTokenCipher(jwtProperties, aesGcmCipher);
    key = new SecretKeySpec(new byte[32], "AES");
    lenient().when(aesGcmCipher.deriveSha256Key("jwt-secret")).thenReturn(key);
  }

  @Test
  void encrypt_encryptsWithDerivedKey() {
    when(aesGcmCipher.encrypt("plain", key)).thenReturn("enc");

    String result = cipher.encrypt("plain");

    assertThat(result).isEqualTo("enc");
    verify(aesGcmCipher).deriveSha256Key("jwt-secret");
    verify(aesGcmCipher).encrypt("plain", key);
  }

  @Test
  void encrypt_rejectsBlankPlaintext() {
    assertThatThrownBy(() -> cipher.encrypt(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("plaintext is required");
  }

  @Test
  void encrypt_rejectsNullPlaintext() {
    assertThatThrownBy(() -> cipher.encrypt(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("plaintext is required");
  }

  @Test
  void encrypt_wrapsTokenEncryptionException() {
    when(aesGcmCipher.encrypt(eq("plain"), any(SecretKey.class)))
        .thenThrow(new TokenEncryptionException("crypto"));

    assertThatThrownBy(() -> cipher.encrypt("plain"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Failed to encrypt token")
        .hasCauseInstanceOf(TokenEncryptionException.class);
  }

  @Test
  void encrypt_wrapsUnexpectedException() {
    when(aesGcmCipher.encrypt(eq("plain"), any(SecretKey.class)))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> cipher.encrypt("plain"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Failed to encrypt token");
  }

  @Test
  void decrypt_decryptsWithDerivedKey() {
    when(aesGcmCipher.decrypt("enc", key)).thenReturn("plain");

    String result = cipher.decrypt("enc");

    assertThat(result).isEqualTo("plain");
    verify(aesGcmCipher).deriveSha256Key("jwt-secret");
    verify(aesGcmCipher).decrypt("enc", key);
  }

  @Test
  void decrypt_rejectsBlankInput() {
    assertThatThrownBy(() -> cipher.decrypt(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("encrypted is required");
  }

  @Test
  void decrypt_rejectsNullInput() {
    assertThatThrownBy(() -> cipher.decrypt(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("encrypted is required");
  }

  @Test
  void decrypt_rethrowsTokenEncryptionException() {
    TokenEncryptionException tokenException = new TokenEncryptionException("invalid");
    when(aesGcmCipher.decrypt(eq("enc"), any(SecretKey.class))).thenThrow(tokenException);

    assertThatThrownBy(() -> cipher.decrypt("enc")).isSameAs(tokenException);
  }

  @Test
  void decrypt_wrapsUnexpectedException() {
    when(aesGcmCipher.decrypt(eq("enc"), any(SecretKey.class)))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> cipher.decrypt("enc"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Failed to decrypt token");
  }

  @Test
  void encrypt_failsWhenJwtSecretMissing() {
    jwtProperties.setSecret(" ");

    assertThatThrownBy(() -> cipher.encrypt("plain"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Failed to encrypt token")
        .hasCauseInstanceOf(TokenEncryptionException.class);
  }

  @Test
  void decrypt_failsWhenJwtSecretMissing() {
    jwtProperties.setSecret(null);

    assertThatThrownBy(() -> cipher.decrypt("enc"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("jwt.secret is required for token encryption");
  }
}
