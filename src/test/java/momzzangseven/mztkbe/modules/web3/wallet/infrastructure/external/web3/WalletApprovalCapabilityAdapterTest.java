package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletApprovalCapabilityAdapter unit test")
class WalletApprovalCapabilityAdapterTest {

  private static final String TOKEN = "0x0000000000000000000000000000000000000001";
  private static final String QNA_ESCROW = "0x0000000000000000000000000000000000000002";
  private static final String MARKETPLACE_ESCROW = "0x0000000000000000000000000000000000000003";
  private static final String BATCH_IMPL = "0x0000000000000000000000000000000000000004";

  @Mock private LoadWalletApprovalSponsorPolicyPort loadSponsorPolicyPort;

  private WalletApprovalProperties properties;
  private WalletApprovalCapabilityAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new WalletApprovalProperties();
    properties.setEnabled(true);
    properties.setChainId(10L);
    properties.setDelegationBatchImplAddress(BATCH_IMPL);
    properties.setTokenContractAddress(TOKEN);
    properties.setQnaEscrowSpenderAddress(QNA_ESCROW);
    properties.setMarketplaceEscrowSpenderAddress(MARKETPLACE_ESCROW);
    properties.setTtlSeconds(300);

    adapter = new WalletApprovalCapabilityAdapter(properties, loadSponsorPolicyPort);
  }

  @Test
  void load_whenConfigurationAndSponsorPolicyAreValid_returnsEnabled() {
    when(loadSponsorPolicyPort.load()).thenReturn(validSponsorPolicy());

    WalletApprovalCapability capability = adapter.load();

    assertThat(capability.available()).isTrue();
    assertThat(capability.reason()).isNull();
  }

  @Test
  void load_whenDelegateTargetIsInvalid_returnsUnavailableBeforeSponsorPolicyLookup() {
    properties.setDelegationBatchImplAddress("invalid");

    WalletApprovalCapability capability = adapter.load();

    assertThat(capability.available()).isFalse();
    assertThat(capability.reason()).isEqualTo("wallet approval configuration is invalid");
    verifyNoInteractions(loadSponsorPolicyPort);
  }

  @Test
  void load_whenStaticSponsorCapIsTooLow_returnsUnavailable() {
    when(loadSponsorPolicyPort.load())
        .thenReturn(
            new WalletApprovalSponsorPolicy(
                true, 500_000L, 60L, new BigDecimal("0.002"), new BigDecimal("0.1")));

    WalletApprovalCapability capability = adapter.load();

    assertThat(capability.available()).isFalse();
    assertThat(capability.reason()).isEqualTo("wallet approval sponsor policy is insufficient");
  }

  private static WalletApprovalSponsorPolicy validSponsorPolicy() {
    return new WalletApprovalSponsorPolicy(
        true, 500_000L, 60L, new BigDecimal("0.05"), new BigDecimal("0.1"));
  }
}
