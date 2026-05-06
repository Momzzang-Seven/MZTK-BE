package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryAddressProjectionPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryWalletJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter for treasury wallets.
 *
 * <p>Projects the {@code TreasuryWallet} aggregate against {@code web3_treasury_wallets}, reading
 * {@code kms_key_id} together with the lifecycle columns ({@code status}, {@code key_origin},
 * {@code disabled_at}). After V065 every row is KMS-backed: {@code treasury_private_key_encrypted}
 * has been dropped and the legacy cipher-backed write path no longer exists.
 */
// TODO TreasuryWalletPersistenceAdapter에 너무 많은 책임이 주어져있다. 별도 객체로 분리.
@Component
@RequiredArgsConstructor
@Slf4j
public class TreasuryWalletPersistenceAdapter
    implements LoadTreasuryWalletPort,
        SaveTreasuryWalletPort,
        ProbeExecutionSignerCapabilityPort,
        LoadTreasuryAddressProjectionPort {

  private final Web3TreasuryWalletJpaRepository repository;
  private final DescribeKmsKeyPort describeKmsKeyPort;

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

  // ----- ProbeExecutionSignerCapabilityPort (KMS-backed probe) -----

  @Override
  public ExecutionSignerCapabilityView probe(String walletAlias) {
    requireNonBlank(walletAlias, "walletAlias");

    return repository
        .findByWalletAlias(walletAlias)
        .map(this::mapCapability)
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

  private ExecutionSignerCapabilityView mapCapability(Web3TreasuryWalletEntity entity) {
    String walletAlias = entity.getWalletAlias();
    boolean hasAddress = hasText(entity.getTreasuryAddress());
    boolean hasKmsKeyId = hasText(entity.getKmsKeyId());

    if (!hasAddress && !hasKmsKeyId) {
      return ExecutionSignerCapabilityView.unprovisioned(walletAlias);
    }
    if (hasAddress != hasKmsKeyId) {
      return ExecutionSignerCapabilityView.unavailable(
          walletAlias,
          ExecutionSignerSlotStatus.UNPROVISIONED,
          ExecutionSignerFailureReason.CORRUPTED_SLOT);
    }
    return mapKmsCapability(entity);
  }

  private ExecutionSignerCapabilityView mapKmsCapability(Web3TreasuryWalletEntity entity) {
    String walletAlias = entity.getWalletAlias();
    TreasuryWalletStatus status = parseStatus(entity.getStatus());
    if (status == TreasuryWalletStatus.DISABLED) {
      return ExecutionSignerCapabilityView.provisionedUnavailable(
          walletAlias, ExecutionSignerFailureReason.WALLET_DISABLED);
    }
    if (status == TreasuryWalletStatus.ARCHIVED) {
      return ExecutionSignerCapabilityView.provisionedUnavailable(
          walletAlias, ExecutionSignerFailureReason.WALLET_ARCHIVED);
    }
    if (status != TreasuryWalletStatus.ACTIVE) {
      return ExecutionSignerCapabilityView.unavailable(
          walletAlias,
          ExecutionSignerSlotStatus.UNPROVISIONED,
          ExecutionSignerFailureReason.KMS_KEY_ID_MISSING);
    }

    KmsKeyState state;
    try {
      state = describeKmsKeyPort.describe(entity.getKmsKeyId());
    } catch (RuntimeException e) {
      log.warn(
          "describeKmsKeyPort failed for alias={} kmsKeyId={}",
          walletAlias,
          entity.getKmsKeyId(),
          e);
      return ExecutionSignerCapabilityView.provisionedUnavailable(
          walletAlias, ExecutionSignerFailureReason.KMS_DESCRIBE_FAILED);
    }

    return switch (state) {
      case ENABLED -> ExecutionSignerCapabilityView.ready(walletAlias, entity.getTreasuryAddress());
      case DISABLED ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_DISABLED);
      case PENDING_DELETION ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_PENDING_DELETION);
      case PENDING_IMPORT ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_PENDING_IMPORT);
      case UNAVAILABLE ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_UNAVAILABLE);
    };
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
