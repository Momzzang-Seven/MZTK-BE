package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoadTreasuryWalletByRoleService 단위 테스트")
class LoadTreasuryWalletByRoleServiceTest {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC);

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;

  @InjectMocks private LoadTreasuryWalletByRoleService service;

  @Test
  @DisplayName("REWARD role → 매칭 wallet 의 view 반환")
  void execute_rewardRole_returnsViewMappedFromWallet() {
    String alias = TreasuryRole.REWARD.toAlias();
    TreasuryWallet wallet =
        TreasuryWallet.provision(
            alias, "kms-id", "0xDeadBeef" + "0".repeat(32), TreasuryRole.REWARD, FIXED);
    when(loadTreasuryWalletPort.loadByAlias(alias)).thenReturn(Optional.of(wallet));

    Optional<TreasuryWalletView> result = service.execute(TreasuryRole.REWARD);

    assertThat(result).isPresent();
    assertThat(result.get().walletAlias()).isEqualTo(alias);
    assertThat(result.get().role()).isEqualTo(TreasuryRole.REWARD);
  }

  @Test
  @DisplayName("매칭 wallet 이 없으면 Optional.empty")
  void execute_walletAbsent_returnsEmpty() {
    when(loadTreasuryWalletPort.loadByAlias(TreasuryRole.SPONSOR.toAlias()))
        .thenReturn(Optional.empty());

    Optional<TreasuryWalletView> result = service.execute(TreasuryRole.SPONSOR);

    assertThat(result).isEmpty();
  }
}
