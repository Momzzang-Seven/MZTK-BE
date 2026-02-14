package momzzangseven.mztkbe.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import momzzangseven.mztkbe.global.error.token.TokenEncryptionException;
import org.springframework.stereotype.Component;

/** Shared AES-256-GCM utility for sensitive payload encryption/decryption. */
@Component
public class AesGcmCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH_BIT = 128;
  private static final int IV_LENGTH_BYTE = 12;
  private static final int KEY_LENGTH_BYTE = 32;

  private final SecureRandom secureRandom = new SecureRandom();

  public String encrypt(String plaintext, SecretKey key) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTE];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));

      byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(iv)
          + "."
          + Base64.getEncoder().encodeToString(ciphertextWithTag);
    } catch (Exception e) {
      throw new TokenEncryptionException("AES-GCM encryption failed", e);
    }
  }

  public String decrypt(String encrypted, SecretKey key) {
    try {
      String[] parts = encrypted.split("\\.", 2);
      if (parts.length != 2) {
        throw new TokenEncryptionException("Invalid encrypted payload format");
      }

      byte[] iv = decodeBase64(parts[0], "Invalid IV base64 format");
      if (iv.length != IV_LENGTH_BYTE) {
        throw new TokenEncryptionException("Invalid IV length");
      }
      byte[] ciphertextWithTag = decodeBase64(parts[1], "Invalid ciphertext base64 format");

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));

      byte[] plaintext = cipher.doFinal(ciphertextWithTag);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (TokenEncryptionException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenEncryptionException("AES-GCM decryption failed", e);
    }
  }

  public SecretKey decodeBase64Key(String keyB64) {
    if (keyB64 == null || keyB64.isBlank()) {
      throw new TokenEncryptionException("encryption key is required");
    }

    byte[] keyBytes = decodeBase64(keyB64, "Invalid base64 key format");
    if (keyBytes.length != KEY_LENGTH_BYTE) {
      throw new TokenEncryptionException("Invalid key length (expected 32 bytes)");
    }
    return new SecretKeySpec(keyBytes, "AES");
  }

  public SecretKey deriveSha256Key(String rawSecret) {
    if (rawSecret == null || rawSecret.isBlank()) {
      throw new TokenEncryptionException("raw secret is required");
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] keyBytes = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(keyBytes, "AES");
    } catch (NoSuchAlgorithmException e) {
      throw new TokenEncryptionException("SHA-256 algorithm not available", e);
    }
  }

  public String generateRandomKeyB64() {
    byte[] bytes = new byte[KEY_LENGTH_BYTE];
    secureRandom.nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private byte[] decodeBase64(String encoded, String message) {
    try {
      return Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new TokenEncryptionException(message, e);
    }
  }
}
