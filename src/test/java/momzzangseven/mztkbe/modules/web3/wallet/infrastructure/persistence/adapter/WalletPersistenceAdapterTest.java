package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.UserWalletEntity;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository.UserWalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletPersistenceAdapterTest {

  @Mock private UserWalletJpaRepository repository;

  private WalletPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WalletPersistenceAdapter(repository);
  }

  @Test
  void findByWalletAddress_mapsEntityToDomain() {
    UserWalletEntity entity =
        UserWalletEntity.builder()
            .id(1L)
            .userId(7L)
            .walletAddress("0x" + "a".repeat(40))
            .status(WalletStatus.ACTIVE)
            .registeredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build();
    when(repository.findByWalletAddress("0x" + "a".repeat(40))).thenReturn(Optional.of(entity));

    Optional<UserWallet> wallet = adapter.findByWalletAddress("0x" + "a".repeat(40));

    assertThat(wallet).isPresent();
    assertThat(wallet.get().getUserId()).isEqualTo(7L);
    assertThat(wallet.get().getStatus()).isEqualTo(WalletStatus.ACTIVE);
  }

  @Test
  void findWalletsByUserIdAndStatus_mapsList() {
    UserWalletEntity entity =
        UserWalletEntity.builder()
            .id(2L)
            .userId(7L)
            .walletAddress("0x" + "b".repeat(40))
            .status(WalletStatus.ACTIVE)
            .registeredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build();
    when(repository.findByUserIdAndStatus(7L, WalletStatus.ACTIVE)).thenReturn(List.of(entity));

    List<UserWallet> wallets = adapter.findWalletsByUserIdAndStatus(7L, WalletStatus.ACTIVE);

    assertThat(wallets).hasSize(1);
    assertThat(wallets.getFirst().getWalletAddress()).isEqualTo("0x" + "b".repeat(40));
  }

  @Test
  void save_mapsDomainToEntityAndBack() {
    UserWallet wallet =
        UserWallet.builder()
            .id(3L)
            .userId(7L)
            .walletAddress("0x" + "c".repeat(40))
            .status(WalletStatus.UNLINKED)
            .registeredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build();
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    UserWallet saved = adapter.save(wallet);

    assertThat(saved.getId()).isEqualTo(3L);
    assertThat(saved.getStatus()).isEqualTo(WalletStatus.UNLINKED);
    verify(repository).save(any(UserWalletEntity.class));
  }
}
