package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryKeyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryKeyPersistenceAdapterTest {

  @Mock private Web3TreasuryKeyJpaRepository repository;
  @Mock private TreasuryKeyCipher treasuryKeyCipher;

  private TreasuryKeyPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TreasuryKeyPersistenceAdapter(repository, treasuryKeyCipher);
  }

  @Test
  void loadByAlias_throws_whenAliasBlank() {
    assertThatThrownBy(() -> adapter.loadByAlias(" ", "kek"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias is required");
  }

  @Test
  void loadByAlias_decryptsAndMapsResult() {
    Web3TreasuryKeyEntity entity =
        Web3TreasuryKeyEntity.builder()
            .walletAlias("reward-main")
            .treasuryAddress("0x" + "a".repeat(40))
            .treasuryPrivateKeyEncrypted("enc")
            .build();
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.of(entity));
    when(treasuryKeyCipher.decrypt("enc", "kek")).thenReturn("f".repeat(64));

    Optional<LoadTreasuryKeyPort.TreasuryKeyMaterial> result =
        adapter.loadByAlias("reward-main", "kek");

    assertThat(result).isPresent();
    assertThat(result.get().treasuryAddress()).isEqualTo("0x" + "a".repeat(40));
    assertThat(result.get().privateKeyHex()).isEqualTo("f".repeat(64));
  }

  @Test
  void upsert_createsNewEntity_whenAliasMissing() {
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.empty());

    adapter.upsert("reward-main", "0x" + "a".repeat(40), "enc");

    ArgumentCaptor<Web3TreasuryKeyEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryKeyEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getWalletAlias()).isEqualTo("reward-main");
    assertThat(captor.getValue().getTreasuryPrivateKeyEncrypted()).isEqualTo("enc");
  }
}
