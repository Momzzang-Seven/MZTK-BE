package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoadTreasuryWalletAdapter")
class LoadTreasuryWalletAdapterTest {

  private static final String WALLET_ALIAS = "reward-treasury";
  private static final String KMS_KEY_ID = "alias/reward-treasury";
  private static final String WALLET_ADDRESS = "0x" + "c".repeat(40);

  @Mock private LoadTreasuryWalletUseCase loadTreasuryWalletUseCase;

  @InjectMocks private LoadTreasuryWalletAdapter adapter;

  @ParameterizedTest(name = "status={0} -> active={1}")
  @CsvSource({"ACTIVE,true", "DISABLED,false", "ARCHIVED,false"})
  @DisplayName("status 가 ACTIVE 일 때만 active=true 로 매핑된다")
  void loadByAlias_mapsStatusToActiveFlag(TreasuryWalletStatus status, boolean expectedActive) {
    when(loadTreasuryWalletUseCase.execute(WALLET_ALIAS)).thenReturn(Optional.of(view(status)));

    Optional<TreasuryWalletInfo> result = adapter.loadByAlias(WALLET_ALIAS);

    assertThat(result).isPresent();
    TreasuryWalletInfo info = result.get();
    assertThat(info.active()).isEqualTo(expectedActive);
    assertThat(info.walletAlias()).isEqualTo(WALLET_ALIAS);
    assertThat(info.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(info.walletAddress()).isEqualTo(WALLET_ADDRESS);
  }

  @org.junit.jupiter.api.Test
  @DisplayName("upstream 이 비어 있으면 어댑터도 빈 Optional 을 반환한다")
  void loadByAlias_emptyUpstream_returnsEmpty() {
    when(loadTreasuryWalletUseCase.execute(WALLET_ALIAS)).thenReturn(Optional.empty());

    Optional<TreasuryWalletInfo> result = adapter.loadByAlias(WALLET_ALIAS);

    assertThat(result).isEmpty();
  }

  private TreasuryWalletView view(TreasuryWalletStatus status) {
    return new TreasuryWalletView(
        WALLET_ALIAS,
        TreasuryRole.REWARD,
        KMS_KEY_ID,
        WALLET_ADDRESS,
        status,
        TreasuryKeyOrigin.IMPORTED,
        LocalDateTime.now(),
        status == TreasuryWalletStatus.ACTIVE ? null : LocalDateTime.now());
  }
}
