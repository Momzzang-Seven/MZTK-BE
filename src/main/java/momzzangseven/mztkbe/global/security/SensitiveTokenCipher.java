package momzzangseven.mztkbe.global.security;

import javax.crypto.SecretKey;
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

  private final JwtProperties jwtProperties;
  private final AesGcmCipher aesGcmCipher;

  /** Encrypt plaintext into base64(iv).base64(ciphertext) using AES-256-GCM. */
  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("plaintext is required");
    }

    try {
      return aesGcmCipher.encrypt(plaintext, deriveKey());
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to encrypt token", e);
    }
  }

  /** Decrypt a value produced by {@link #encrypt(String)}. */
  public String decrypt(String encrypted) {
    if (encrypted == null || encrypted.isBlank()) {
      throw new IllegalArgumentException("encrypted is required");
    }

    try {
      return aesGcmCipher.decrypt(encrypted, deriveKey());
    } catch (TokenEncryptionException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to decrypt token", e);
    }
  }

  private SecretKey deriveKey() {
    String secret = jwtProperties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new TokenEncryptionException("jwt.secret is required for token encryption");
    }
    return aesGcmCipher.deriveSha256Key(secret);
  }
}
