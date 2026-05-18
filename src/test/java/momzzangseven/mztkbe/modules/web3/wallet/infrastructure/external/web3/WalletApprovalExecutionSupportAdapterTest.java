package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareEip7702AuthorizationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionSupport;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletApprovalExecutionSupportAdapter unit test")
class WalletApprovalExecutionSupportAdapterTest {

  private static final String AUTHORITY = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String BATCH_IMPL = "0x0000000000000000000000000000000000000004";
  private static final String HASH = "0x" + "c".repeat(64);

  @Mock private PrepareEip7702AuthorizationUseCase prepareEip7702AuthorizationUseCase;

  private WalletApprovalProperties approvalProperties;
  private WalletApprovalExecutionSupportAdapter adapter;

  @BeforeEach
  void setUp() {
    approvalProperties = new WalletApprovalProperties();
    approvalProperties.setChainId(10L);
    approvalProperties.setDelegationBatchImplAddress(BATCH_IMPL);
    approvalProperties.setTtlSeconds(300);

    adapter =
        new WalletApprovalExecutionSupportAdapter(
            prepareEip7702AuthorizationUseCase, approvalProperties);
  }

  @Test
  void load_preparesAuthorizationThroughEip7702InputUseCase() {
    when(prepareEip7702AuthorizationUseCase.execute(any()))
        .thenReturn(new PrepareEip7702AuthorizationResult(7L, HASH));

    WalletApprovalExecutionSupport support = adapter.load(AUTHORITY);

    assertThat(support.chainId()).isEqualTo(10L);
    assertThat(support.delegateTarget()).isEqualTo(BATCH_IMPL);
    assertThat(support.authorityNonce()).isEqualTo(7L);
    assertThat(support.authorizationPayloadHash()).isEqualTo(HASH);
    assertThat(support.ttlSeconds()).isEqualTo(300);

    ArgumentCaptor<PrepareEip7702AuthorizationCommand> captor =
        ArgumentCaptor.forClass(PrepareEip7702AuthorizationCommand.class);
    verify(prepareEip7702AuthorizationUseCase).execute(captor.capture());
    assertThat(captor.getValue().chainId()).isEqualTo(10L);
    assertThat(captor.getValue().delegateTarget()).isEqualTo(BATCH_IMPL);
    assertThat(captor.getValue().authorityAddress()).isEqualTo(AUTHORITY);
  }
}
