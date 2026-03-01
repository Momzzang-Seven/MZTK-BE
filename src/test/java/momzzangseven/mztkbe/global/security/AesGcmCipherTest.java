package momzzangseven.mztkbe.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKey;
import momzzangseven.mztkbe.global.error.token.TokenEncryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

  private AesGcmCipher cipher;

  @BeforeEach
  void setUp() {
    cipher = new AesGcmCipher();
  }

  @Test
  void encryptDecrypt_roundTrip() {
    SecretKey key = cipher.decodeBase64Key(cipher.generateRandomKeyB64());
    String plaintext = "sensitive-value";

    String encrypted = cipher.encrypt(plaintext, key);
    String decrypted = cipher.decrypt(encrypted, key);

    assertThat(encrypted).contains(".");
    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void decrypt_rejectsInvalidPayloadFormat() {
    SecretKey key = cipher.decodeBase64Key(cipher.generateRandomKeyB64());

    assertThatThrownBy(() -> cipher.decrypt("invalid-format", key))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Invalid encrypted payload format");
  }

  @Test
  void decrypt_rejectsInvalidIvBase64() {
    SecretKey key = cipher.decodeBase64Key(cipher.generateRandomKeyB64());
    String ciphertext = Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> cipher.decrypt("%%%." + ciphertext, key))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Invalid IV base64 format");
  }

  @Test
  void decrypt_rejectsInvalidIvLength() {
    SecretKey key = cipher.decodeBase64Key(cipher.generateRandomKeyB64());
    String shortIv = Base64.getEncoder().encodeToString(new byte[3]);
    String ciphertext = Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> cipher.decrypt(shortIv + "." + ciphertext, key))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Invalid IV length");
  }

  @Test
  void decrypt_rejectsInvalidCiphertextBase64() {
    SecretKey key = cipher.decodeBase64Key(cipher.generateRandomKeyB64());
    String iv = Base64.getEncoder().encodeToString(new byte[12]);

    assertThatThrownBy(() -> cipher.decrypt(iv + ".%%%", key))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Invalid ciphertext base64 format");
  }

  @Test
  void decrypt_withWrongKey_throws() {
    SecretKey key = cipher.decodeBase64Key(cipher.generateRandomKeyB64());
    SecretKey wrongKey = cipher.decodeBase64Key(cipher.generateRandomKeyB64());
    String encrypted = cipher.encrypt("hello", key);

    assertThatThrownBy(() -> cipher.decrypt(encrypted, wrongKey))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("AES-GCM decryption failed");
  }

  @Test
  void decodeBase64Key_rejectsBlank() {
    assertThatThrownBy(() -> cipher.decodeBase64Key(" "))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("encryption key is required");
  }

  @Test
  void decodeBase64Key_rejectsNull() {
    assertThatThrownBy(() -> cipher.decodeBase64Key(null))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("encryption key is required");
  }

  @Test
  void decodeBase64Key_rejectsInvalidBase64() {
    assertThatThrownBy(() -> cipher.decodeBase64Key("%%%"))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Invalid base64 key format");
  }

  @Test
  void decodeBase64Key_rejectsWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> cipher.decodeBase64Key(shortKey))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("Invalid key length (expected 32 bytes)");
  }

  @Test
  void decodeBase64Key_accepts32ByteKey() {
    byte[] raw = new byte[32];
    for (int i = 0; i < raw.length; i++) {
      raw[i] = (byte) i;
    }
    SecretKey key = cipher.decodeBase64Key(Base64.getEncoder().encodeToString(raw));

    assertThat(key.getAlgorithm()).isEqualTo("AES");
    assertThat(key.getEncoded()).hasSize(32);
  }

  @Test
  void deriveSha256Key_rejectsBlank() {
    assertThatThrownBy(() -> cipher.deriveSha256Key(" "))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("raw secret is required");
  }

  @Test
  void deriveSha256Key_rejectsNull() {
    assertThatThrownBy(() -> cipher.deriveSha256Key(null))
        .isInstanceOf(TokenEncryptionException.class)
        .hasMessage("raw secret is required");
  }

  @Test
  void deriveSha256Key_isDeterministic() throws Exception {
    SecretKey key = cipher.deriveSha256Key("my-secret");
    byte[] expected =
        MessageDigest.getInstance("SHA-256").digest("my-secret".getBytes(StandardCharsets.UTF_8));

    assertThat(key.getAlgorithm()).isEqualTo("AES");
    assertThat(key.getEncoded()).isEqualTo(expected);
  }

  @Test
  void generateRandomKeyB64_returns32ByteBase64() {
    String first = cipher.generateRandomKeyB64();
    String second = cipher.generateRandomKeyB64();

    assertThat(Base64.getDecoder().decode(first)).hasSize(32);
    assertThat(Base64.getDecoder().decode(second)).hasSize(32);
    assertThat(first).isNotEqualTo(second);
  }
}
