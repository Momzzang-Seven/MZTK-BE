package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
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
@Component
@RequiredArgsConstructor
@Slf4j
public class TreasuryWalletPersistenceAdapter
    implements LoadTreasuryWalletPort, SaveTreasuryWalletPort {

  private final Web3TreasuryWalletJpaRepository repository;

  // ----- LoadTreasuryWalletPort / SaveTreasuryWalletPort (KMS-backed) -----

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
    // V065 enforces NOT NULL on status / key_origin (and the entity mirrors with nullable=false).
    // Surfacing the violation at the adapter boundary turns an opaque SQL constraint error into a
    // domain-level invariant failure that names the offending field.
    requireNonBlank(wallet.getWalletAlias(), "walletAlias");
    requireNonBlank(wallet.getKmsKeyId(), "kmsKeyId");
    requireNonBlank(wallet.getWalletAddress(), "walletAddress");
    if (wallet.getStatus() == null) {
      throw new Web3InvalidInputException("status is required");
    }
    if (wallet.getKeyOrigin() == null) {
      throw new Web3InvalidInputException("keyOrigin is required");
    }
    entity.setWalletAlias(wallet.getWalletAlias());
    entity.setKmsKeyId(wallet.getKmsKeyId());
    entity.setTreasuryAddress(wallet.getWalletAddress());
    entity.setStatus(wallet.getStatus().name());
    entity.setKeyOrigin(wallet.getKeyOrigin().name());
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
}
