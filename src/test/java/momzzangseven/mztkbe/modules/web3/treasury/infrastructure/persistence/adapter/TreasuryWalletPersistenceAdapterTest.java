package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
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

  private static final String ALIAS = "sponsor-treasury";
  private static final String ADDRESS = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";
  private static final String KMS_KEY_ID = "4229019f-0fef-4049-af16-850de547606f";

  @Mock private Web3TreasuryWalletJpaRepository repository;

  private TreasuryWalletPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TreasuryWalletPersistenceAdapter(repository);
  }

  // ----- save: applyDomain guards (KMS-finalize cleanup migration NOT NULL mirror) -----

  @Test
  void save_persistsAndReturnsDomain_whenAllRequiredFieldsPresent() {
    TreasuryWallet input = validWalletBuilder().build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());
    when(repository.save(any(Web3TreasuryWalletEntity.class)))
        .thenAnswer(
            invocation -> {
              Web3TreasuryWalletEntity arg = invocation.getArgument(0);
              arg.setId(7L);
              return arg;
            });

    TreasuryWallet saved = adapter.save(input);

    ArgumentCaptor<Web3TreasuryWalletEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryWalletEntity.class);
    verify(repository).save(captor.capture());
    Web3TreasuryWalletEntity entity = captor.getValue();
    assertThat(entity.getWalletAlias()).isEqualTo(ALIAS);
    assertThat(entity.getKmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(entity.getTreasuryAddress()).isEqualTo(ADDRESS);
    assertThat(entity.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE.name());
    assertThat(entity.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED.name());

    assertThat(saved.getId()).isEqualTo(7L);
    assertThat(saved.getWalletAlias()).isEqualTo(ALIAS);
    assertThat(saved.getKmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(saved.getWalletAddress()).isEqualTo(ADDRESS);
    assertThat(saved.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    assertThat(saved.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED);
  }

  @Test
  void save_updatesExistingRow_whenAliasAlreadyPresent_preservingId() {
    Web3TreasuryWalletEntity existing =
        Web3TreasuryWalletEntity.builder()
            .id(42L)
            .walletAlias(ALIAS)
            .treasuryAddress("0x" + "a".repeat(40))
            .kmsKeyId("legacy-kms-id")
            .status(TreasuryWalletStatus.ACTIVE.name())
            .keyOrigin(TreasuryKeyOrigin.IMPORTED.name())
            .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
            .updatedAt(LocalDateTime.of(2025, 1, 1, 0, 0))
            .build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(existing));
    when(repository.save(any(Web3TreasuryWalletEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TreasuryWallet input = validWalletBuilder().build();

    TreasuryWallet saved = adapter.save(input);

    ArgumentCaptor<Web3TreasuryWalletEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryWalletEntity.class);
    verify(repository).save(captor.capture());
    Web3TreasuryWalletEntity entity = captor.getValue();
    assertThat(entity.getId()).isEqualTo(42L);
    assertThat(entity.getKmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(entity.getTreasuryAddress()).isEqualTo(ADDRESS);
    assertThat(saved.getId()).isEqualTo(42L);
  }

  @Test
  void save_throwsInvalidInput_whenWalletNull() {
    assertThatThrownBy(() -> adapter.save(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("wallet");
    verify(repository, never()).save(any());
  }

  @Test
  void save_throwsInvalidInput_whenWalletAliasBlank() {
    TreasuryWallet input = validWalletBuilder().walletAlias("").build();
    when(repository.findByWalletAlias("")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.save(input))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias");
    verify(repository, never()).save(any());
  }

  @Test
  void save_throwsInvalidInput_whenKmsKeyIdBlank() {
    TreasuryWallet input = validWalletBuilder().kmsKeyId("").build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.save(input))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("kmsKeyId");
    verify(repository, never()).save(any());
  }

  @Test
  void save_throwsInvalidInput_whenWalletAddressBlank() {
    TreasuryWallet input = validWalletBuilder().walletAddress("").build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.save(input))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAddress");
    verify(repository, never()).save(any());
  }

  @Test
  void save_throwsInvalidInput_whenStatusNull() {
    TreasuryWallet input = validWalletBuilder().status(null).build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.save(input))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status");
    verify(repository, never()).save(any());
  }

  @Test
  void save_throwsInvalidInput_whenKeyOriginNull() {
    TreasuryWallet input = validWalletBuilder().keyOrigin(null).build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.save(input))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("keyOrigin");
    verify(repository, never()).save(any());
  }

  // ----- loadByAlias -----

  @Test
  void loadByAlias_throwsInvalidInput_whenAliasBlank() {
    assertThatThrownBy(() -> adapter.loadByAlias(""))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias");
    assertThatThrownBy(() -> adapter.loadByAlias(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias");
    verify(repository, never()).findByWalletAlias(ALIAS);
  }

  @Test
  void loadByAlias_returnsEmpty_whenRepositoryEmpty() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    Optional<TreasuryWallet> result = adapter.loadByAlias(ALIAS);

    assertThat(result).isEmpty();
  }

  @Test
  void loadByAlias_returnsDomain_whenRepositoryHasEntity() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder()
            .id(11L)
            .walletAlias(ALIAS)
            .treasuryAddress(ADDRESS)
            .kmsKeyId(KMS_KEY_ID)
            .status(TreasuryWalletStatus.DISABLED.name())
            .keyOrigin(TreasuryKeyOrigin.IMPORTED.name())
            .build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    TreasuryWallet wallet = adapter.loadByAlias(ALIAS).orElseThrow();

    assertThat(wallet.getId()).isEqualTo(11L);
    assertThat(wallet.getWalletAlias()).isEqualTo(ALIAS);
    assertThat(wallet.getKmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(wallet.getWalletAddress()).isEqualTo(ADDRESS);
    assertThat(wallet.getStatus()).isEqualTo(TreasuryWalletStatus.DISABLED);
    assertThat(wallet.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED);
  }

  // ----- loadByAliasForUpdate -----

  @Test
  @org.junit.jupiter.api.DisplayName("loadByAliasForUpdate 가 findByWalletAliasForUpdate 를 경유한다")
  void loadByAliasForUpdate_delegatesToRepository() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder()
            .id(1L)
            .walletAlias(ALIAS)
            .kmsKeyId("kms")
            .treasuryAddress(ADDRESS)
            .status("ACTIVE")
            .keyOrigin("IMPORTED")
            .createdAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .build();
    when(repository.findByWalletAliasForUpdate(ALIAS)).thenReturn(Optional.of(entity));

    Optional<TreasuryWallet> result = adapter.loadByAliasForUpdate(ALIAS);

    assertThat(result).isPresent();
    verify(repository).findByWalletAliasForUpdate(ALIAS);
  }

  // ----- helpers -----

  private static TreasuryWallet.TreasuryWalletBuilder validWalletBuilder() {
    return TreasuryWallet.builder()
        .walletAlias(ALIAS)
        .kmsKeyId(KMS_KEY_ID)
        .walletAddress(ADDRESS)
        .status(TreasuryWalletStatus.ACTIVE)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED);
  }
}
