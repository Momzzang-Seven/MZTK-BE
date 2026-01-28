package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.UserWalletEntity;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository.UserWalletJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletPersistenceAdapter implements LoadWalletPort, SaveWalletPort, DeleteWalletPort {

  private final UserWalletJpaRepository repository;

  // ====== Load Wallet Port Implementation ======//

  @Override
  public Optional<UserWallet> findById(Long walletId) {
    return repository.findById(walletId).map(this::mapToDomain);
  }

  @Override
  public Optional<UserWallet> findByWalletAddress(String walletAddress) {
    return repository.findByWalletAddress(walletAddress).map(this::mapToDomain);
  }

  @Override
  public Optional<UserWallet> findByWalletAddressAndStatus(
      String walletAddress, WalletStatus status) {
    return repository.findByWalletAddressAndStatus(walletAddress, status).map(this::mapToDomain);
  }

  @Override
  public boolean existsByWalletAddress(String walletAddress) {
    return repository.existsByWalletAddress(walletAddress);
  }

  @Override
  public boolean existsByWalletAddressAndStatus(String walletAddress, WalletStatus status) {
    return repository.existsByWalletAddressAndStatus(walletAddress, status);
  }

  @Override
  public int countWalletsByUserIdAndStatus(Long userId, WalletStatus status) {
    return repository.countByUserIdAndStatus(userId, status);
  }

  @Override
  public List<UserWallet> findWalletsByUserIdAndStatus(Long userId, WalletStatus status) {
    return repository.findByUserIdAndStatus(userId, status).stream()
        .map(this::mapToDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<WalletStatus> getWalletStatus(String walletAddress) {
    return repository.findByWalletAddress(walletAddress).map(UserWalletEntity::getStatus);
  }

  @Override
  public List<WalletDeletionInfo> loadWalletsForDeletion(Instant cutoffDate, int limit) {
    Pageable pageable =
        PageRequest.of(0, limit, Sort.by(Sort.Order.asc("unlinkedAt"), Sort.Order.asc("id")));
    return repository.findWalletsForDeletion(cutoffDate, pageable);
  }

  @Override
  public List<WalletDeletionInfo> findWalletsByUserIdAndUserDeleted(List<Long> userIds) {
    return repository.findWalletsByUserIdInAndUserDeleted(userIds);
  }

  // ====== Save Wallet Port Implementation ======//
  @Override
  public UserWallet save(UserWallet wallet) {
    UserWalletEntity entity = mapToEntity(wallet);
    UserWalletEntity savedEntity = repository.save(entity);
    return mapToDomain(savedEntity);
  }

  // ====== Delete Wallet Port Implementation ======//
  @Override
  public void deleteById(Long id) {
    repository.deleteById(id);
  }

  @Override
  public void deleteAllByIdInBatch(List<Long> ids) {
    repository.deleteAllByIdInBatch(ids);
  }

  // ====== Mapping Methods ======//

  private UserWallet mapToDomain(UserWalletEntity entity) {
    return UserWallet.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .walletAddress(entity.getWalletAddress())
        .status(entity.getStatus())
        .registeredAt(entity.getRegisteredAt())
        .unlinkedAt(entity.getUnlinkedAt())
        .userDeletedAt(entity.getUserDeletedAt())
        .build();
  }

  private UserWalletEntity mapToEntity(UserWallet wallet) {
    return UserWalletEntity.builder()
        .id(wallet.getId())
        .userId(wallet.getUserId())
        .walletAddress(wallet.getWalletAddress())
        .status(wallet.getStatus())
        .registeredAt(wallet.getRegisteredAt())
        .unlinkedAt(wallet.getUnlinkedAt())
        .userDeletedAt(wallet.getUserDeletedAt())
        .build();
  }
}
