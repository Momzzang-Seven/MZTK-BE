package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadRewardTreasuryAddressAdapterTest {

  private static final String REWARD_ADDRESS = "0x" + "a".repeat(40);

  @Mock private LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;
  @InjectMocks private LoadRewardTreasuryAddressAdapter adapter;

  @Test
  void loadAddress_delegatesWithRewardRole_andExtractsWalletAddress() {
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.REWARD))
        .thenReturn(Optional.of(view(REWARD_ADDRESS)));

    Optional<String> result = adapter.loadAddress();

    assertThat(result).contains(REWARD_ADDRESS);
    verify(loadTreasuryWalletByRoleUseCase).execute(TreasuryRole.REWARD);
  }

  @Test
  void loadAddress_returnsEmpty_whenTreasuryRowAbsent() {
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.REWARD)).thenReturn(Optional.empty());

    Optional<String> result = adapter.loadAddress();

    assertThat(result).isEmpty();
  }

  @Test
  void loadAddress_propagatesNullAddress_whenViewAddressIsNull() {
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.REWARD))
        .thenReturn(Optional.of(view(null)));

    Optional<String> result = adapter.loadAddress();

    assertThat(result).isEmpty();
  }

  private static TreasuryWalletView view(String walletAddress) {
    return new TreasuryWalletView(
        TreasuryRole.REWARD.toAlias(),
        TreasuryRole.REWARD,
        "kms-key-id",
        walletAddress,
        TreasuryWalletStatus.ACTIVE,
        TreasuryKeyOrigin.IMPORTED,
        null,
        null);
  }
}
