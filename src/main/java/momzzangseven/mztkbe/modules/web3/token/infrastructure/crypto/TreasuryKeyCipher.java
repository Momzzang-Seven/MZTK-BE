package momzzangseven.mztkbe.modules.web3.token.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.token.TokenEncryptionException;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import org.springframework.stereotype.Component;

/** AES-256-GCM cipher for treasury private key payloads. */
@Component
@RequiredArgsConstructor
public class TreasuryKeyCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH_BIT = 128;
  private static final int IV_LENGTH_BYTE = 12;
  private static final int KEY_LENGTH_BYTE = 32;

  private final RewardTokenProperties rewardTokenProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public String encryptWithConfiguredKey(String plaintext) {
    String keyB64 = rewardTokenProperties.getTreasury().getKeyEncryptionKeyB64();
    return encrypt(plaintext, keyB64);
  }

  public String decryptWithConfiguredKey(String encrypted) {
    String keyB64 = rewardTokenProperties.getTreasury().getKeyEncryptionKeyB64();
    return decrypt(encrypted, keyB64);
  }

  public String encrypt(String plaintext, String keyB64) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("plaintext is required");
    }

    try {
      byte[] iv = new byte[IV_LENGTH_BYTE];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, decodeKey(keyB64), new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));

      byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(iv)
          + "."
          + Base64.getEncoder().encodeToString(ciphertextWithTag);
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to encrypt treasury key", e);
    }
  }

  public String decrypt(String encrypted, String keyB64) {
    if (encrypted == null || encrypted.isBlank()) {
      throw new IllegalArgumentException("encrypted is required");
    }

    try {
      String[] parts = encrypted.split("\\.", 2);
      if (parts.length != 2) {
        throw new TokenEncryptionException("Invalid encrypted treasury key format");
      }

      byte[] iv = Base64.getDecoder().decode(parts[0]);
      if (iv.length != IV_LENGTH_BYTE) {
        throw new TokenEncryptionException("Invalid IV length for treasury key");
      }
      byte[] ciphertextWithTag = Base64.getDecoder().decode(parts[1]);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, decodeKey(keyB64), new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));

      byte[] plaintext = cipher.doFinal(ciphertextWithTag);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (TokenEncryptionException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to decrypt treasury key", e);
    }
  }

  public String generateKeyB64() {
    byte[] bytes = new byte[KEY_LENGTH_BYTE];
    secureRandom.nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private SecretKey decodeKey(String keyB64) {
    if (keyB64 == null || keyB64.isBlank()) {
      throw new TokenEncryptionException("treasury key encryption key is required");
    }

    try {
      byte[] keyBytes = Base64.getDecoder().decode(keyB64);
      if (keyBytes.length != KEY_LENGTH_BYTE) {
        throw new TokenEncryptionException("Invalid key length (expected 32 bytes)");
      }
      return new SecretKeySpec(keyBytes, "AES");
    } catch (IllegalArgumentException e) {
      throw new TokenEncryptionException("Invalid base64 key format", e);
    }
  }
}
