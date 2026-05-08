package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryWalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @Test
  void loadAddressByAlias_returnsStoredAddressProjection_whenPresent() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(activeKmsEntity()));

    var result = adapter.loadAddressByAlias(ALIAS);

    assertThat(result).contains(ADDRESS);
  }

  @Test
  void loadAddressByAlias_returnsEmpty_whenAliasNotFound() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    var result = adapter.loadAddressByAlias(ALIAS);

    assertThat(result).isEmpty();
  }

  @Test
  void loadAddressByAlias_returnsEmpty_whenAddressBlank() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder().walletAlias(ALIAS).treasuryAddress("").build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.loadAddressByAlias(ALIAS);

    assertThat(result).isEmpty();
  }

  private static Web3TreasuryWalletEntity activeKmsEntity() {
    return Web3TreasuryWalletEntity.builder()
        .walletAlias(ALIAS)
        .treasuryAddress(ADDRESS)
        .kmsKeyId(KMS_KEY_ID)
        .status(TreasuryWalletStatus.ACTIVE.name())
        .build();
  }
}
