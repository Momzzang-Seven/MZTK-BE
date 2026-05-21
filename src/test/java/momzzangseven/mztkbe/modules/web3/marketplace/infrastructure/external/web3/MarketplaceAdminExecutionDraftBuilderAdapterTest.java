package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceAdminEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminExecutionRequestSource;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceAdminExecutionDraftBuilderAdapter")
class MarketplaceAdminExecutionDraftBuilderAdapterTest {

  private static final String ESCROW = "0x4444444444444444444444444444444444444444";
  private static final String SIGNER = "0x5555555555555555555555555555555555555555";

  @Mock private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  @Mock private LoadInternalExecutionEip1559TtlPort loadInternalExecutionEip1559TtlPort;
  @Mock private BuildMarketplaceAdminEscrowCallDataPort buildCallDataPort;
  @Mock private MarketplaceContractCallSupport marketplaceContractCallSupport;

  private MarketplaceAdminExecutionDraftBuilderAdapter sut;

  @BeforeEach
  void setUp() {
    MarketplaceEscrowProperties escrowProperties = new MarketplaceEscrowProperties();
    escrowProperties.setMarketplaceContractAddress(ESCROW);
    Web3CoreProperties coreProperties = new Web3CoreProperties();
    coreProperties.setChainId(11155111L);
    sut =
        new MarketplaceAdminExecutionDraftBuilderAdapter(
            loadSponsorTreasuryWalletPort,
            verifyTreasuryWalletForSignPort,
            loadInternalExecutionEip1559TtlPort,
            coreProperties,
            escrowProperties,
            buildCallDataPort,
            marketplaceContractCallSupport,
            new MarketplacePayloadSerializer(new ObjectMapper().findAndRegisterModules()),
            new MarketplaceUnsignedTxFingerprintFactory(),
            Clock.fixed(Instant.parse("2026-05-20T01:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  @DisplayName("manual admin refund draft는 direct EIP-1559 unsigned tx와 v2 admin payload를 만든다")
  void build_manualAdminRefundDraft() {
    givenSignerReady();
    given(buildCallDataPort.encodeAdminRefund(request().orderKey())).willReturn("0xadminrefund");
    given(marketplaceContractCallSupport.prevalidateContractCall(SIGNER, ESCROW, "0xadminrefund"))
        .willReturn(
            new MarketplaceContractCallSupport.MarketplaceCallPrevalidationResult(
                BigInteger.valueOf(80_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(50_000_000_000L)));
    given(loadInternalExecutionEip1559TtlPort.loadTtlSeconds()).willReturn(120L);

    MarketplaceExecutionDraft draft = sut.build(request());

    assertThat(draft.actionType())
        .isEqualTo(MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND);
    assertThat(draft.fallbackAllowed()).isFalse();
    assertThat(draft.authorityAddress()).isNull();
    assertThat(draft.unsignedTxSnapshot()).isNotNull();
    assertThat(draft.unsignedTxSnapshot().fromAddress()).isEqualTo(SIGNER);
    assertThat(draft.unsignedTxSnapshot().data()).isEqualTo("0xadminrefund");
    assertThat(draft.expiresAt()).isEqualTo(LocalDateTime.parse("2026-05-20T01:02:00"));
    assertThat(draft.payloadSnapshotJson()).contains("\"payloadVersion\":2");
    assertThat(draft.payloadSnapshotJson()).contains("\"requestSource\":\"MANUAL_ADMIN\"");
    assertThat(draft.payloadSnapshotJson()).contains("\"adminProvenanceActor\":\"ADMIN\"");
    assertThat(draft.payloadSnapshotJson()).contains("\"memo\":\"operator memo\"");
    assertThat(draft.rootIdempotencyKey())
        .isEqualTo("marketplace-admin:marketplace_admin_refund:123:manual_admin:trainer_timeout");
  }

  @Test
  @DisplayName("sponsor signer가 없으면 draft build를 차단한다")
  void build_rejectsMissingSigner() {
    given(loadSponsorTreasuryWalletPort.load()).willReturn(Optional.empty());

    assertThatThrownBy(() -> sut.build(request()))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("SERVER_SIGNER_UNAVAILABLE");

    then(buildCallDataPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("relayer 미등록이면 draft build를 차단한다")
  void build_rejectsUnregisteredRelayer() {
    given(loadSponsorTreasuryWalletPort.load())
        .willReturn(Optional.of(new TreasuryWalletInfo("sponsor", "kms", SIGNER, true)));
    given(marketplaceContractCallSupport.isRelayerRegistered(ESCROW, SIGNER)).willReturn(false);

    assertThatThrownBy(() -> sut.build(request()))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("RELAYER_NOT_REGISTERED");

    then(buildCallDataPort).shouldHaveNoInteractions();
  }

  private void givenSignerReady() {
    given(loadSponsorTreasuryWalletPort.load())
        .willReturn(Optional.of(new TreasuryWalletInfo("sponsor", "kms", SIGNER, true)));
    given(marketplaceContractCallSupport.isRelayerRegistered(ESCROW, SIGNER)).willReturn(true);
  }

  private MarketplaceAdminEscrowExecutionRequest request() {
    return new MarketplaceAdminEscrowExecutionRequest(
        MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
        123L,
        "123",
        "123e4567-e89b-12d3-a456-426614174000",
        "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
        10L,
        20L,
        10L,
        20L,
        3L,
        "ADMIN_REFUND_PENDING",
        "ADMIN_REFUND_PENDING",
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222",
        "0x3333333333333333333333333333333333333333",
        BigInteger.valueOf(50_000),
        50_000,
        LocalDateTime.parse("2026-05-20T10:00:00"),
        "attempt-token",
        "TIMEOUT_CANCELLED",
        900L,
        901L,
        MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN,
        77L,
        null,
        "TRAINER_TIMEOUT",
        " operator memo ",
        null);
  }
}
