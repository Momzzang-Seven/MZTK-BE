package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryWalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletPersistenceAdapterTest {

  private static final String MATCHING_ADDRESS = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";

  @Mock private Web3TreasuryWalletJpaRepository repository;
  @Mock private TreasuryKeyCipher treasuryKeyCipher;

  private TreasuryWalletPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TreasuryWalletPersistenceAdapter(repository, treasuryKeyCipher);
  }

  @Test
  void probe_returnsProvisionedStatus_whenKeyEncryptionKeyMissing() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder()
            .walletAlias("reward-main")
            .treasuryAddress("0x" + "a".repeat(40))
            .treasuryPrivateKeyEncrypted("enc")
            .build();
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.of(entity));

    var result = adapter.probe("reward-main", null);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason())
        .isEqualTo(ExecutionSignerFailureReason.KEY_ENCRYPTION_KEY_MISSING);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsProvisionedStatus_whenDecryptFails() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder()
            .walletAlias("reward-main")
            .treasuryAddress("0x" + "a".repeat(40))
            .treasuryPrivateKeyEncrypted("enc")
            .build();
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.of(entity));
    when(treasuryKeyCipher.decrypt("enc", "kek")).thenThrow(new Web3InvalidInputException("bad"));

    var result = adapter.probe("reward-main", "kek");

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.DECRYPT_FAILED);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void loadAddressByAlias_returnsStoredAddressProjection_whenPresent() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder()
            .walletAlias("reward-main")
            .treasuryAddress(MATCHING_ADDRESS)
            .treasuryPrivateKeyEncrypted("enc")
            .build();
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.of(entity));

    var result = adapter.loadAddressByAlias("reward-main");

    assertThat(result).contains(MATCHING_ADDRESS);
  }

  @Test
  void upsert_createsNewEntity_whenAliasMissing() {
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.empty());

    adapter.upsert("reward-main", "0x" + "a".repeat(40), "enc");

    ArgumentCaptor<Web3TreasuryWalletEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryWalletEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getWalletAlias()).isEqualTo("reward-main");
    assertThat(captor.getValue().getTreasuryPrivateKeyEncrypted()).isEqualTo("enc");
  }

  @Test
  void upsert_updatesExistingEntity_whenAliasAlreadyExists() {
    Web3TreasuryWalletEntity existing =
        Web3TreasuryWalletEntity.builder()
            .id(1L)
            .walletAlias("reward-main")
            .treasuryAddress("0x" + "a".repeat(40))
            .treasuryPrivateKeyEncrypted("old-enc")
            .build();
    when(repository.findByWalletAlias("reward-main")).thenReturn(Optional.of(existing));

    adapter.upsert("reward-main", "0x" + "b".repeat(40), "new-enc");

    ArgumentCaptor<Web3TreasuryWalletEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryWalletEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(1L);
    assertThat(captor.getValue().getWalletAlias()).isEqualTo("reward-main");
    assertThat(captor.getValue().getTreasuryAddress()).isEqualTo("0x" + "b".repeat(40));
    assertThat(captor.getValue().getTreasuryPrivateKeyEncrypted()).isEqualTo("new-enc");
  }
}
