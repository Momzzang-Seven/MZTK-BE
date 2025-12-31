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
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.token.TokenEncryptionException;
import org.springframework.stereotype.Component;

/**
 * Encrypt sensitive third-party tokens (e.g., Google OAuth refresh token) before persisting.
 *
 * <p>Implementation details:
 *
 * <ul>
 *   <li>Algorithm: AES-256-GCM
 *   <li>Key derivation: SHA-256(jwt.secret)
 *   <li>Encoding: base64(iv).base64(ciphertext)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SensitiveTokenCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH_BIT = 128;
  private static final int IV_LENGTH_BYTE = 12;

  private final JwtProperties jwtProperties;

  private final SecureRandom secureRandom = new SecureRandom();

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("plaintext is required");
    }

    try {
      byte[] iv = new byte[IV_LENGTH_BYTE];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));

      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      return Base64.getEncoder().encodeToString(iv)
          + "."
          + Base64.getEncoder().encodeToString(ciphertext);
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to encrypt token", e);
    }
  }

  private SecretKey deriveKey() {
    String secret = jwtProperties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new TokenEncryptionException("jwt.secret is required for token encryption");
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(keyBytes, "AES");
    } catch (NoSuchAlgorithmException e) {
      throw new TokenEncryptionException("SHA-256 algorithm not available", e);
    }
  }
}
