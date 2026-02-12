package momzzangseven.mztkbe.modules.web3.token.application.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.RecordWeb3AdminActionAuditPort;
import momzzangseven.mztkbe.modules.web3.admin.domain.model.Web3AdminActionType;
import momzzangseven.mztkbe.modules.web3.admin.domain.model.Web3AdminTargetType;
import momzzangseven.mztkbe.modules.web3.token.api.dto.ProvisionTreasuryKeyResponseDTO;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.crypto.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.transaction.application.support.AuditDetailBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProvisionTreasuryKeyService implements ProvisionTreasuryKeyUseCase {

  private final TreasuryKeyCipher treasuryKeyCipher;
  private final SaveTreasuryKeyPort saveTreasuryKeyPort;
  private final RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;
  private final RecordWeb3AdminActionAuditPort recordWeb3AdminActionAuditPort;

  @Override
  @Transactional
  public ProvisionTreasuryKeyResponseDTO execute(Long operatorId, String rawPrivateKey) {
    String treasuryAddress = null;

    try {
      String normalizedPrivateKey = normalizePrivateKey(rawPrivateKey);
      treasuryAddress = Credentials.create(normalizedPrivateKey).getAddress().toLowerCase();

      String encryptionKeyB64 = treasuryKeyCipher.generateKeyB64();
      String encrypted = treasuryKeyCipher.encrypt(normalizedPrivateKey, encryptionKeyB64);

      saveTreasuryKeyPort.upsert(treasuryAddress, encrypted);

      recordAudit(operatorId, treasuryAddress, true, null);
      recordAdminAudit(operatorId, treasuryAddress, true, null);

      return ProvisionTreasuryKeyResponseDTO.builder()
          .treasuryAddress(treasuryAddress)
          .treasuryPrivateKeyEncrypted(encrypted)
          .treasuryKeyEncryptionKeyB64(encryptionKeyB64)
          .build();
    } catch (RuntimeException e) {
      recordAudit(operatorId, treasuryAddress, false, e.getClass().getSimpleName());
      recordAdminAudit(operatorId, treasuryAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }

  private String normalizePrivateKey(String rawPrivateKey) {
    if (rawPrivateKey == null || rawPrivateKey.isBlank()) {
      throw new TreasuryPrivateKeyInvalidException("treasuryPrivateKey is required");
    }

    String normalized = rawPrivateKey.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("0x")) {
      normalized = normalized.substring(2);
    }

    if (normalized.length() != 64) {
      throw new TreasuryPrivateKeyInvalidException("treasuryPrivateKey must be 32-byte hex");
    }

    if (!normalized.matches("^[0-9a-f]{64}$")) {
      throw new TreasuryPrivateKeyInvalidException("treasuryPrivateKey must be hex string");
    }

    return normalized;
  }

  private void recordAudit(
      Long operatorId, String treasuryAddress, boolean success, String failureReason) {
    try {
      recordTreasuryProvisionAuditPort.record(
          new RecordTreasuryProvisionAuditPort.AuditCommand(
              operatorId, treasuryAddress, success, failureReason));
    } catch (Exception e) {
      log.warn(
          "Failed to record treasury provision audit: operatorId={}, success={}",
          operatorId,
          success,
          e);
    }
  }

  private void recordAdminAudit(
      Long operatorId, String treasuryAddress, boolean success, String failureReason) {
    try {
      recordWeb3AdminActionAuditPort.record(
          new RecordWeb3AdminActionAuditPort.AuditCommand(
              operatorId,
              Web3AdminActionType.TREASURY_KEY_PROVISION,
              Web3AdminTargetType.TREASURY_KEY,
              treasuryAddress,
              success,
              AuditDetailBuilder.create()
                  .put("treasuryAddress", treasuryAddress)
                  .put("success", success)
                  .put("failureReason", failureReason)
                  .build()));
    } catch (Exception e) {
      log.warn(
          "Failed to record web3 admin action audit: action={}, operatorId={}",
          Web3AdminActionType.TREASURY_KEY_PROVISION,
          operatorId,
          e);
    }
  }
}
