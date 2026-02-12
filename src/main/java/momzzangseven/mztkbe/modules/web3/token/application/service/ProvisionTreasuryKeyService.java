package momzzangseven.mztkbe.modules.web3.token.application.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.api.dto.ProvisionTreasuryKeyResponseDTO;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.crypto.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryKeyJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;

@Service
@RequiredArgsConstructor
public class ProvisionTreasuryKeyService {

  private static final short SINGLETON_ID = 1;

  private final TreasuryKeyCipher treasuryKeyCipher;
  private final Web3TreasuryKeyJpaRepository web3TreasuryKeyJpaRepository;

  @Transactional
  public ProvisionTreasuryKeyResponseDTO provision(String rawPrivateKey) {
    String normalizedPrivateKey = normalizePrivateKey(rawPrivateKey);
    String treasuryAddress = Credentials.create(normalizedPrivateKey).getAddress().toLowerCase();

    String encryptionKeyB64 = treasuryKeyCipher.generateKeyB64();
    String encrypted = treasuryKeyCipher.encrypt(normalizedPrivateKey, encryptionKeyB64);

    Web3TreasuryKeyEntity entity =
        web3TreasuryKeyJpaRepository
            .findById(SINGLETON_ID)
            .orElseGet(() -> Web3TreasuryKeyEntity.builder().id(SINGLETON_ID).build());
    entity.setTreasuryAddress(treasuryAddress);
    entity.setTreasuryPrivateKeyEncrypted(encrypted);
    web3TreasuryKeyJpaRepository.save(entity);

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
