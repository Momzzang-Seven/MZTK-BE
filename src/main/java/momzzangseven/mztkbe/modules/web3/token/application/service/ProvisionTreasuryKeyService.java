package momzzangseven.mztkbe.modules.web3.token.application.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.api.dto.ProvisionTreasuryKeyResponseDTO;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.crypto.TreasuryKeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;

@Service
@RequiredArgsConstructor
public class ProvisionTreasuryKeyService implements ProvisionTreasuryKeyUseCase {

  private final TreasuryKeyCipher treasuryKeyCipher;
  private final SaveTreasuryKeyPort saveTreasuryKeyPort;

  @Override
  @Transactional
  public ProvisionTreasuryKeyResponseDTO execute(String rawPrivateKey) {
    String normalizedPrivateKey = normalizePrivateKey(rawPrivateKey);
    String treasuryAddress = Credentials.create(normalizedPrivateKey).getAddress().toLowerCase();

    String encryptionKeyB64 = treasuryKeyCipher.generateKeyB64();
    String encrypted = treasuryKeyCipher.encrypt(normalizedPrivateKey, encryptionKeyB64);

    saveTreasuryKeyPort.upsert(treasuryAddress, encrypted);

    return ProvisionTreasuryKeyResponseDTO.builder()
        .treasuryAddress(treasuryAddress)
        .treasuryPrivateKeyEncrypted(encrypted)
        .treasuryKeyEncryptionKeyB64(encryptionKeyB64)
        .build();
  }

  private String normalizePrivateKey(String rawPrivateKey) {
    if (rawPrivateKey == null || rawPrivateKey.isBlank()) {
      throw new IllegalArgumentException("treasuryPrivateKey is required");
    }

    String normalized = rawPrivateKey.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("0x")) {
      normalized = normalized.substring(2);
    }

    if (normalized.length() != 64) {
      throw new IllegalArgumentException("treasuryPrivateKey must be 32-byte hex");
    }

    if (!normalized.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("treasuryPrivateKey must be hex string");
    }

    return normalized;
  }
}
