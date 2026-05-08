package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoadSponsorTreasuryWalletAdapter")
class LoadSponsorTreasuryWalletAdapterTest {

  private static final String WALLET_ALIAS = "sponsor-treasury";
  private static final String KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String WALLET_ADDRESS = "0x" + "d".repeat(40);

  @Mock private LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @InjectMocks private LoadSponsorTreasuryWalletAdapter adapter;

  @ParameterizedTest(name = "status={0} -> active={1}")
  @CsvSource({"ACTIVE,true", "DISABLED,false", "ARCHIVED,false"})
  @DisplayName("status 가 ACTIVE 일 때만 active=true 로 매핑된다")
  void load_mapsStatusToActiveFlag(TreasuryWalletStatus status, boolean expectedActive) {
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.SPONSOR))
        .thenReturn(Optional.of(view(status)));

    Optional<TreasuryWalletInfo> result = adapter.load();

    assertThat(result).isPresent();
    TreasuryWalletInfo info = result.get();
    assertThat(info.active()).isEqualTo(expectedActive);
    assertThat(info.walletAlias()).isEqualTo(WALLET_ALIAS);
    assertThat(info.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(info.walletAddress()).isEqualTo(WALLET_ADDRESS);
  }

  @Test
  @DisplayName("upstream 이 비어 있으면 어댑터도 빈 Optional 을 반환한다")
  void load_emptyUpstream_returnsEmpty() {
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.SPONSOR))
        .thenReturn(Optional.empty());

    Optional<TreasuryWalletInfo> result = adapter.load();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("@Component 빈 이름이 executionLoadSponsorTreasuryWalletAdapter")
  void componentBeanName_isNamespacedToExecutionModule() {
    Component component = LoadSponsorTreasuryWalletAdapter.class.getAnnotation(Component.class);

    assertThat(component).isNotNull();
    assertThat(component.value()).isEqualTo("executionLoadSponsorTreasuryWalletAdapter");
  }

  private TreasuryWalletView view(TreasuryWalletStatus status) {
    return new TreasuryWalletView(
        WALLET_ALIAS,
        TreasuryRole.SPONSOR,
        KMS_KEY_ID,
        WALLET_ADDRESS,
        status,
        TreasuryKeyOrigin.IMPORTED,
        LocalDateTime.now(),
        status == TreasuryWalletStatus.ACTIVE ? null : LocalDateTime.now());
  }
}
