package momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter;

import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.TokenEncryptionException;
import momzzangseven.mztkbe.global.security.AesGcmCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import org.springframework.stereotype.Component;

/** AES-256-GCM cipher for treasury private key payloads. */
@Component
@RequiredArgsConstructor
public class TreasuryKeyCipher {

  private final RewardTokenProperties rewardTokenProperties;
  private final AesGcmCipher aesGcmCipher;

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
      throw new Web3InvalidInputException("plaintext is required");
    }

    try {
      return aesGcmCipher.encrypt(plaintext, decodeKey(keyB64));
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to encrypt treasury key", e);
    }
  }

  public String decrypt(String encrypted, String keyB64) {
    if (encrypted == null || encrypted.isBlank()) {
      throw new Web3InvalidInputException("encrypted is required");
    }

    try {
      return aesGcmCipher.decrypt(encrypted, decodeKey(keyB64));
    } catch (TokenEncryptionException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenEncryptionException("Failed to decrypt treasury key", e);
    }
  }

  public String generateKeyB64() {
    return aesGcmCipher.generateRandomKeyB64();
  }

  private SecretKey decodeKey(String keyB64) {
    if (keyB64 == null || keyB64.isBlank()) {
      throw new TokenEncryptionException("treasury key encryption key is required");
    }
    return aesGcmCipher.decodeBase64Key(keyB64);
  }
}
