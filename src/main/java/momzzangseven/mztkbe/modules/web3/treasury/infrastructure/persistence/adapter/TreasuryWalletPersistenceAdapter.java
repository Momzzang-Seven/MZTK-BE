package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryAddressProjectionPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryWalletJpaRepository;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;

/**
 * Persistence adapter for treasury wallets.
 *
 * <p>Implements two cohorts of ports during the KMS migration window:
 *
 * <ul>
 *   <li><b>New (KMS-backed)</b> — {@link LoadTreasuryWalletPort} / {@link SaveTreasuryWalletPort}
 *       project the {@code TreasuryWallet} aggregate, reading {@code kms_key_id} and the new
 *       lifecycle columns added in V056.
 *   <li><b>Legacy (cipher-backed)</b> — {@link LoadTreasuryKeyPort} / {@link SaveTreasuryKeyPort}
 *       continue to drive the historical encrypted-private-key path so {@code transaction} (until
 *       PR2) and {@code eip7702} / {@code execution} (until PR3) can still issue signatures while
 *       the new KMS path is being adopted.
 * </ul>
 *
 * <p>Both port families share the same JPA repository / entity; rows that have only legacy columns
 * populated remain readable through the legacy methods, and rows that have only KMS columns
 * populated are visible to the new methods.
 */
@Component
@RequiredArgsConstructor
public class TreasuryWalletPersistenceAdapter
    implements LoadTreasuryWalletPort,
        SaveTreasuryWalletPort,
        LoadTreasuryKeyPort,
        SaveTreasuryKeyPort,
        ProbeExecutionSignerCapabilityPort,
        LoadTreasuryAddressProjectionPort {

  private final Web3TreasuryWalletJpaRepository repository;
  private final TreasuryKeyCipher treasuryKeyCipher;

  // ----- LoadTreasuryWalletPort / SaveTreasuryWalletPort (new, KMS-backed) -----

  @Override
  public Optional<TreasuryWallet> loadByAlias(String walletAlias) {
    requireNonBlank(walletAlias, "walletAlias");
    return repository
        .findByWalletAlias(walletAlias)
        .map(TreasuryWalletPersistenceAdapter::toDomain);
  }

  @Override
  public boolean existsAddressOwnedByOther(String walletAlias, String walletAddress) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(walletAddress, "walletAddress");
    return repository.existsByTreasuryAddressAndWalletAliasNot(walletAddress, walletAlias);
  }

  @Override
  public TreasuryWallet save(TreasuryWallet wallet) {
    if (wallet == null) {
      throw new Web3InvalidInputException("wallet must not be null");
    }
    Web3TreasuryWalletEntity entity =
        repository
            .findByWalletAlias(wallet.getWalletAlias())
            .orElseGet(() -> Web3TreasuryWalletEntity.builder().build());
    applyDomain(entity, wallet);
    Web3TreasuryWalletEntity saved = repository.save(entity);
    return toDomain(saved);
  }

  // ----- LoadTreasuryKeyPort (legacy cipher path, retained until PR2/PR4) -----

  @Override
  public Optional<TreasuryKeyMaterial> loadByAlias(String walletAlias, String kekB64) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(kekB64, "kekB64");
    return repository
        .findByWalletAlias(walletAlias)
        .filter(this::hasProvisionedSlotMaterial)
        .flatMap(entity -> resolveProvisionedMaterial(entity, kekB64).material());
  }

  // ----- ProbeExecutionSignerCapabilityPort (legacy probe) -----

  @Override
  public ExecutionSignerCapabilityView probe(String walletAlias, String keyEncryptionKeyB64) {
    requireNonBlank(walletAlias, "walletAlias");

    return repository
        .findByWalletAlias(walletAlias)
        .map(entity -> mapCapability(entity, keyEncryptionKeyB64))
        .orElseGet(() -> ExecutionSignerCapabilityView.slotMissing(walletAlias));
  }

  // ----- LoadTreasuryAddressProjectionPort -----

  @Override
  public Optional<String> loadAddressByAlias(String walletAlias) {
    requireNonBlank(walletAlias, "walletAlias");

    return repository
        .findByWalletAlias(walletAlias)
        .map(Web3TreasuryWalletEntity::getTreasuryAddress)
        .filter(TreasuryWalletPersistenceAdapter::hasText);
  }

  // ----- SaveTreasuryKeyPort (legacy upsert) -----

  @Override
  public void upsert(
      String walletAlias, String treasuryAddress, String treasuryPrivateKeyEncrypted) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(treasuryAddress, "treasuryAddress");
    requireNonBlank(treasuryPrivateKeyEncrypted, "treasuryPrivateKeyEncrypted");

    Web3TreasuryWalletEntity entity =
        repository
            .findByWalletAlias(walletAlias)
            .orElseGet(() -> Web3TreasuryWalletEntity.builder().build());
    entity.setWalletAlias(walletAlias);
    entity.setTreasuryAddress(treasuryAddress);
    entity.setTreasuryPrivateKeyEncrypted(treasuryPrivateKeyEncrypted);
    repository.save(entity);
  }

  // ----- helpers -----

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private static TreasuryWallet toDomain(Web3TreasuryWalletEntity entity) {
    return TreasuryWallet.builder()
        .id(entity.getId())
        .walletAlias(entity.getWalletAlias())
        .kmsKeyId(entity.getKmsKeyId())
        .walletAddress(entity.getTreasuryAddress())
        .status(parseStatus(entity.getStatus()))
        .keyOrigin(parseKeyOrigin(entity.getKeyOrigin()))
        .disabledAt(entity.getDisabledAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private static void applyDomain(Web3TreasuryWalletEntity entity, TreasuryWallet wallet) {
    entity.setWalletAlias(wallet.getWalletAlias());
    entity.setKmsKeyId(wallet.getKmsKeyId());
    entity.setTreasuryAddress(wallet.getWalletAddress());
    entity.setStatus(wallet.getStatus() == null ? null : wallet.getStatus().name());
    entity.setKeyOrigin(wallet.getKeyOrigin() == null ? null : wallet.getKeyOrigin().name());
    entity.setDisabledAt(wallet.getDisabledAt());
    if (wallet.getCreatedAt() != null) {
      entity.setCreatedAt(wallet.getCreatedAt());
    }
    if (wallet.getUpdatedAt() != null) {
      entity.setUpdatedAt(wallet.getUpdatedAt());
    }
  }

  private static TreasuryWalletStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return TreasuryWalletStatus.valueOf(value);
  }

  private static TreasuryKeyOrigin parseKeyOrigin(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return TreasuryKeyOrigin.valueOf(value);
  }

  private ExecutionSignerCapabilityView mapCapability(
      Web3TreasuryWalletEntity entity, String keyEncryptionKeyB64) {
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

  private boolean hasProvisionedSlotMaterial(Web3TreasuryWalletEntity entity) {
    return hasText(entity.getTreasuryAddress()) && hasText(entity.getTreasuryPrivateKeyEncrypted());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private ProvisionedMaterialResolution resolveProvisionedMaterial(
      Web3TreasuryWalletEntity entity, String keyEncryptionKeyB64) {
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
