package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionSupport;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionSupportPort;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletApprovalExecutionDraftBuilderAdapter unit test")
class WalletApprovalExecutionDraftBuilderAdapterTest {

  private static final String TOKEN = "0x0000000000000000000000000000000000000001";
  private static final String QNA_ESCROW = "0x0000000000000000000000000000000000000002";
  private static final String MARKETPLACE_ESCROW = "0x0000000000000000000000000000000000000003";
  private static final String BATCH_IMPL = "0x0000000000000000000000000000000000000004";
  private static final String WALLET = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  @Mock private LoadWalletApprovalExecutionSupportPort loadWalletApprovalExecutionSupportPort;

  private WalletApprovalProperties approvalProperties;
  private WalletApprovalExecutionDraftBuilderAdapter adapter;

  @BeforeEach
  void setUp() {
    approvalProperties = new WalletApprovalProperties();
    approvalProperties.setEnabled(true);
    approvalProperties.setTokenContractAddress(TOKEN);
    approvalProperties.setQnaEscrowSpenderAddress(QNA_ESCROW);
    approvalProperties.setMarketplaceEscrowSpenderAddress(MARKETPLACE_ESCROW);
    approvalProperties.setTtlSeconds(300);

    adapter =
        new WalletApprovalExecutionDraftBuilderAdapter(
            loadWalletApprovalExecutionSupportPort,
            approvalProperties,
            new WalletApprovalCalldataEncoder(),
            new WalletApprovalPayloadSerializer(new ObjectMapper()),
            Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
  }

  @Test
  void build_createsTwoZeroValueMaxApproveCallsToTokenContract() {
    givenExecutionSupport("0x" + "f".repeat(64), 12L);

    WalletApprovalExecutionDraft draft =
        adapter.build(new WalletApprovalExecutionRequest("registration-1", 7L, WALLET));

    assertThat(draft.resourceType().name()).isEqualTo("WALLET_REGISTRATION");
    assertThat(draft.actionType().name()).isEqualTo("WALLET_ESCROW_APPROVE");
    assertThat(draft.resourceId()).isEqualTo("registration-1");
    assertThat(draft.rootIdempotencyKey()).isEqualTo("wallet-registration-approval:registration-1");
    assertThat(draft.authorityAddress()).isEqualTo(WALLET);
    assertThat(draft.authorityNonce()).isEqualTo(12L);
    assertThat(draft.delegateTarget()).isEqualTo(BATCH_IMPL);
    assertThat(draft.authorizationPayloadHash()).isEqualTo("0x" + "f".repeat(64));
    assertThat(draft.fallbackAllowed()).isFalse();
    assertThat(draft.unsignedTxSnapshot()).isNull();
    assertThat(draft.expiresAt()).isEqualTo(LocalDateTime.parse("2026-05-13T09:05:00"));

    assertThat(draft.calls()).hasSize(2);
    assertThat(draft.calls()).allSatisfy(call -> assertThat(call.value()).isZero());
    assertThat(draft.calls()).allSatisfy(call -> assertThat(call.target()).isEqualTo(TOKEN));
    assertThat(draft.calls().get(0).data()).isEqualTo(expectedApproveMax(QNA_ESCROW));
    assertThat(draft.calls().get(1).data()).isEqualTo(expectedApproveMax(MARKETPLACE_ESCROW));
  }

  @Test
  void build_storesApprovalCallsInPayloadSnapshot() throws Exception {
    givenExecutionSupport("0x" + "a".repeat(64), 1L);

    WalletApprovalExecutionDraft draft =
        adapter.build(new WalletApprovalExecutionRequest("registration-1", 7L, WALLET));

    var payload = new ObjectMapper().readTree(draft.payloadSnapshotJson());
    assertThat(payload.get("registrationId").asText()).isEqualTo("registration-1");
    assertThat(payload.get("walletAddress").asText()).isEqualTo(WALLET);
    assertThat(payload.get("tokenAddress").asText()).isEqualTo(TOKEN);
    assertThat(payload.get("approvals").size()).isEqualTo(2);
    assertThat(payload.get("approvals").get(0).get("spender").asText()).isEqualTo(QNA_ESCROW);
    assertThat(payload.get("approvals").get(1).get("spender").asText())
        .isEqualTo(MARKETPLACE_ESCROW);
  }

  @Test
  void build_throwsApprovalUnavailable_whenApprovalFlowDisabled() {
    approvalProperties.setEnabled(false);

    assertThatThrownBy(
            () -> adapter.build(new WalletApprovalExecutionRequest("registration-1", 7L, WALLET)))
        .isInstanceOf(WalletApprovalUnavailableException.class)
        .hasMessageContaining("wallet approval flow is disabled");
  }

  private String expectedApproveMax(String spender) {
    return "0x095ea7b3" + "0".repeat(24) + spender.substring(2) + "f".repeat(64);
  }

  private void givenExecutionSupport(String authorizationPayloadHash, long authorityNonce) {
    org.mockito.Mockito.when(loadWalletApprovalExecutionSupportPort.load(WALLET))
        .thenReturn(
            new WalletApprovalExecutionSupport(
                11155111L, BATCH_IMPL, authorityNonce, authorizationPayloadHash, 300));
  }
}
