package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

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
  public int countActiveWalletsByUserId(Long userId) {
    return repository.countActiveWalletsByUserId(userId);
  }

  @Override
  public List<UserWallet> findActiveWalletsByUserId(Long userId) {
    return repository.findActiveWalletsByUserId(userId).stream()
        .map(this::mapToDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<WalletStatus> getWalletStatus(String walletAddress) {
    return repository.findByWalletAddress(walletAddress).map(UserWalletEntity::getStatus);
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
  public Long deleteWalletInBatch(Long walletId) {
    if (repository.existsById(walletId)) {
      repository.deleteById(walletId);
      return walletId;
    }
    return null;
  }

  // ====== Mapping Methods ======//

  private UserWallet mapToDomain(UserWalletEntity entity) {
    return UserWallet.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .walletAddress(entity.getWalletAddress())
        .status(entity.getStatus())
        .registeredAt(entity.getRegisteredAt())
        .build();
  }

  private UserWalletEntity mapToEntity(UserWallet wallet) {
    return UserWalletEntity.builder()
        .id(wallet.getId())
        .userId(wallet.getUserId())
        .walletAddress(wallet.getWalletAddress())
        .status(wallet.getStatus())
        .registeredAt(wallet.getRegisteredAt())
        .build();
  }
}
