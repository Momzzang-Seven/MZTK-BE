package momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import momzzangseven.mztkbe.global.error.token.TokenEncryptionException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryKeyCipherTest {

  @Mock private AesGcmCipher aesGcmCipher;

  private TreasuryKeyCipher cipher;

  @BeforeEach
  void setUp() {
    cipher = new TreasuryKeyCipher(aesGcmCipher);
  }

  @Test
  void encrypt_throws_whenPlaintextBlank() {
    assertThatThrownBy(() -> cipher.encrypt(" ", "key-b64"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("plaintext is required");
  }

  @Test
  void encrypt_throws_whenPlaintextNull() {
    assertThatThrownBy(() -> cipher.encrypt(null, "key-b64"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("plaintext is required");
  }

  @Test
  void encrypt_throws_whenKeyBlank() {
    assertThatThrownBy(() -> cipher.encrypt("secret", " "))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessageContaining("Failed to encrypt treasury key");
  }

  @Test
  void encrypt_wrapsException_whenUnderlyingCipherFails() throws Exception {
    SecretKey key = new SecretKeySpec(new byte[32], "AES");
    when(aesGcmCipher.decodeBase64Key("key-b64")).thenReturn(key);
    when(aesGcmCipher.encrypt("secret", key)).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> cipher.encrypt("secret", "key-b64"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessageContaining("Failed to encrypt treasury key");
  }

  @Test
  void decrypt_throws_whenEncryptedBlank() {
    assertThatThrownBy(() -> cipher.decrypt(" ", "key-b64"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("encrypted is required");
  }

  @Test
  void decrypt_throws_whenEncryptedNull() {
    assertThatThrownBy(() -> cipher.decrypt(null, "key-b64"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("encrypted is required");
  }

  @Test
  void decrypt_rethrowsTokenEncryptionException_asIs() throws Exception {
    SecretKey key = new SecretKeySpec(new byte[32], "AES");
    TokenEncryptionException expected = new TokenEncryptionException("decrypt failed");
    when(aesGcmCipher.decodeBase64Key("key-b64")).thenReturn(key);
    when(aesGcmCipher.decrypt("encrypted", key)).thenThrow(expected);

    assertThatThrownBy(() -> cipher.decrypt("encrypted", "key-b64")).isSameAs(expected);
  }

  @Test
  void decrypt_wrapsUnexpectedException() throws Exception {
    SecretKey key = new SecretKeySpec(new byte[32], "AES");
    when(aesGcmCipher.decodeBase64Key("key-b64")).thenReturn(key);
    when(aesGcmCipher.decrypt("encrypted", key)).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> cipher.decrypt("encrypted", "key-b64"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessageContaining("Failed to decrypt treasury key");
  }

  @Test
  void generateKeyB64_delegatesToCipher() {
    when(aesGcmCipher.generateRandomKeyB64()).thenReturn("generated-key");

    String key = cipher.generateKeyB64();

    assertThat(key).isEqualTo("generated-key");
    verify(aesGcmCipher).generateRandomKeyB64();
  }
}
