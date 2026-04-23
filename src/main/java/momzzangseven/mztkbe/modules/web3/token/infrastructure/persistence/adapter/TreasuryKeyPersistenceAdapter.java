package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryAddressProjectionPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryKeyJpaRepository;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;

@Component
@RequiredArgsConstructor
public class TreasuryKeyPersistenceAdapter
    implements LoadTreasuryKeyPort,
        SaveTreasuryKeyPort,
        ProbeExecutionSignerCapabilityPort,
        LoadTreasuryAddressProjectionPort {

  private final Web3TreasuryKeyJpaRepository repository;
  private final TreasuryKeyCipher treasuryKeyCipher;

  @Override
  public Optional<TreasuryKeyMaterial> loadByAlias(String walletAlias, String kekB64) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(kekB64, "kekB64");
    return repository
        .findByWalletAlias(walletAlias)
        .filter(this::hasProvisionedSlotMaterial)
        .flatMap(entity -> resolveProvisionedMaterial(entity, kekB64).material());
  }

  @Override
  public ExecutionSignerCapabilityView probe(String walletAlias, String keyEncryptionKeyB64) {
    requireNonBlank(walletAlias, "walletAlias");

    return repository
        .findByWalletAlias(walletAlias)
        .map(entity -> mapCapability(entity, keyEncryptionKeyB64))
        .orElseGet(() -> ExecutionSignerCapabilityView.slotMissing(walletAlias));
  }

  @Override
  public Optional<String> loadAddressByAlias(String walletAlias) {
    requireNonBlank(walletAlias, "walletAlias");

    return repository
        .findByWalletAlias(walletAlias)
        .map(Web3TreasuryKeyEntity::getTreasuryAddress)
        .filter(TreasuryKeyPersistenceAdapter::hasText);
  }

  @Override
  public void upsert(
      String walletAlias, String treasuryAddress, String treasuryPrivateKeyEncrypted) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(treasuryAddress, "treasuryAddress");
    requireNonBlank(treasuryPrivateKeyEncrypted, "treasuryPrivateKeyEncrypted");

    Web3TreasuryKeyEntity entity =
        repository
            .findByWalletAlias(walletAlias)
            .orElseGet(() -> Web3TreasuryKeyEntity.builder().build());
    entity.setWalletAlias(walletAlias);
    entity.setTreasuryAddress(treasuryAddress);
    entity.setTreasuryPrivateKeyEncrypted(treasuryPrivateKeyEncrypted);
    repository.save(entity);
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private ExecutionSignerCapabilityView mapCapability(
      Web3TreasuryKeyEntity entity, String keyEncryptionKeyB64) {
    String walletAlias = entity.getWalletAlias();
    boolean hasAddress = hasText(entity.getTreasuryAddress());
    boolean hasEncryptedKey = hasText(entity.getTreasuryPrivateKeyEncrypted());

    if (!hasAddress && !hasEncryptedKey) {
      return ExecutionSignerCapabilityView.unprovisioned(walletAlias);
    }
    if (hasAddress != hasEncryptedKey) {
      return ExecutionSignerCapabilityView.unavailable(
          walletAlias,
          ExecutionSignerSlotStatus.UNPROVISIONED,
          ExecutionSignerFailureReason.CORRUPTED_SLOT);
    }
    ProvisionedMaterialResolution resolution =
        resolveProvisionedMaterial(entity, keyEncryptionKeyB64);
    if (resolution.material().isPresent()) {
      return ExecutionSignerCapabilityView.ready(walletAlias, entity.getTreasuryAddress());
    }
    return ExecutionSignerCapabilityView.provisionedUnavailable(
        walletAlias, resolution.failureReason());
  }

  private boolean hasProvisionedSlotMaterial(Web3TreasuryKeyEntity entity) {
    return hasText(entity.getTreasuryAddress()) && hasText(entity.getTreasuryPrivateKeyEncrypted());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private ProvisionedMaterialResolution resolveProvisionedMaterial(
      Web3TreasuryKeyEntity entity, String keyEncryptionKeyB64) {
    if (!hasText(keyEncryptionKeyB64)) {
      return ProvisionedMaterialResolution.failure(
          ExecutionSignerFailureReason.KEY_ENCRYPTION_KEY_MISSING);
    }

    try {
      String privateKeyHex =
          treasuryKeyCipher.decrypt(entity.getTreasuryPrivateKeyEncrypted(), keyEncryptionKeyB64);
      String derivedAddress = Credentials.create(privateKeyHex).getAddress().toLowerCase();
      if (!derivedAddress.equalsIgnoreCase(entity.getTreasuryAddress())) {
        return ProvisionedMaterialResolution.failure(ExecutionSignerFailureReason.ADDRESS_MISMATCH);
      }
      return ProvisionedMaterialResolution.success(
          TreasuryKeyMaterial.of(entity.getTreasuryAddress(), privateKeyHex));
    } catch (RuntimeException e) {
      return ProvisionedMaterialResolution.failure(ExecutionSignerFailureReason.DECRYPT_FAILED);
    }
  }

  private record ProvisionedMaterialResolution(
      Optional<TreasuryKeyMaterial> material, ExecutionSignerFailureReason failureReason) {

    private static ProvisionedMaterialResolution success(TreasuryKeyMaterial material) {
      return new ProvisionedMaterialResolution(
          Optional.of(material), ExecutionSignerFailureReason.NONE);
    }

    private static ProvisionedMaterialResolution failure(
        ExecutionSignerFailureReason failureReason) {
      return new ProvisionedMaterialResolution(Optional.empty(), failureReason);
    }
  }
}
