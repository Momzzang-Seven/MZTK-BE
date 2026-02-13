package momzzangseven.mztkbe.modules.web3.token.application.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.token.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter.crypto.TreasuryKeyCipher;
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

  @Override
  @Transactional
  @AdminOnly(
      actionType = "TREASURY_KEY_PROVISION",
      targetType = "TREASURY_KEY",
      operatorId = "#operatorId",
      targetId = "#result != null ? #result.treasuryAddress() : null")
  public ProvisionTreasuryKeyResult execute(Long operatorId, String rawPrivateKey) {
    validateOperatorId(operatorId);
    String treasuryAddress = null;

    try {
      String normalizedPrivateKey = normalizePrivateKey(rawPrivateKey);
      treasuryAddress = Credentials.create(normalizedPrivateKey).getAddress().toLowerCase();

      String encryptionKeyB64 = treasuryKeyCipher.generateKeyB64();
      String encrypted = treasuryKeyCipher.encrypt(normalizedPrivateKey, encryptionKeyB64);

      saveTreasuryKeyPort.upsert(treasuryAddress, encrypted);

      recordAudit(operatorId, treasuryAddress, true, null);

      return ProvisionTreasuryKeyResult.of(treasuryAddress, encrypted, encryptionKeyB64);
    } catch (RuntimeException e) {
      recordAudit(operatorId, treasuryAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }

  private void validateOperatorId(Long operatorId) {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
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
}
