package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEip7702DraftContextPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SignMarketplaceServerSigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceUserExecutionDraftBuilderAdapter 단위 테스트")
class MarketplaceUserExecutionDraftBuilderAdapterTest {

  private static final String BUYER = "0x1111111111111111111111111111111111111111";
  private static final String TRAINER = "0x2222222222222222222222222222222222222222";
  private static final String TOKEN = "0x3333333333333333333333333333333333333333";
  private static final String ESCROW = "0x4444444444444444444444444444444444444444";
  private static final String DELEGATE = "0x5555555555555555555555555555555555555555";
  private static final String ORDER_ID = "123e4567-e89b-12d3-a456-426614174000";
  private static final String ORDER_KEY =
      "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000";
  private static final byte[] SIGNATURE = new byte[65];
  private static final Instant CONTEXT_INSTANT = Instant.ofEpochSecond(900);
  private static final Instant SIGNING_INSTANT = Instant.ofEpochSecond(1_000);

  @Mock private LoadMarketplaceActiveWalletPort loadMarketplaceActiveWalletPort;
  @Mock private LoadMarketplaceEip7702DraftContextPort loadMarketplaceEip7702DraftContextPort;
  @Mock private BuildMarketplaceEscrowCallDataPort buildMarketplaceEscrowCallDataPort;
  @Mock private SignMarketplaceServerSigPort signMarketplaceServerSigPort;

  private MarketplaceUserExecutionDraftBuilderAdapter sut;

  @BeforeEach
  void setUp() {
    MarketplaceEscrowProperties marketplaceEscrowProperties = new MarketplaceEscrowProperties();
    marketplaceEscrowProperties.setMarketplaceContractAddress(ESCROW);
    marketplaceEscrowProperties.setSigValidityDuration(900);

    sut =
        new MarketplaceUserExecutionDraftBuilderAdapter(
            loadMarketplaceActiveWalletPort,
            loadMarketplaceEip7702DraftContextPort,
            marketplaceEscrowProperties,
            buildMarketplaceEscrowCallDataPort,
            signMarketplaceServerSigPort,
            new MarketplacePayloadSerializer(new ObjectMapper().findAndRegisterModules()),
            Clock.fixed(CONTEXT_INSTANT, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("purchase draft는 signer의 signingInstant로 expiresAt을 계산하고 EIP-7702 전용 draft를 만든다")
  void build_purchase_usesSigningInstantForExpiresAt() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER));
    givenDraftContext();
    given(signMarketplaceServerSigPort.sign(any()))
        .willReturn(new MarketplaceServerSigResult(1_000L, SIGNATURE, SIGNING_INSTANT));
    given(
            buildMarketplaceEscrowCallDataPort.encode(
                any(), any(), any(), any(), any(), anyLong(), any()))
        .willReturn("0xpurchase");

    MarketplaceExecutionDraft draft =
        sut.build(request(MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE));

    assertThat(draft.calls()).hasSize(1);
    assertThat(draft.calls().getFirst().target()).isEqualTo(ESCROW);
    assertThat(draft.calls().getFirst().data()).isEqualTo("0xpurchase");
    assertThat(draft.fallbackAllowed()).isFalse();
    assertThat(draft.unsignedTxSnapshot()).isNull();
    assertThat(draft.authorityAddress()).isEqualTo(BUYER);
    assertThat(draft.authorityNonce()).isEqualTo(7L);
    assertThat(draft.delegateTarget()).isEqualTo(DELEGATE);
    assertThat(draft.authorizationPayloadHash()).isEqualTo("0xauthorizationHash");
    assertThat(draft.signatureMeta().signedAt()).isEqualTo(1_000L);
    assertThat(draft.signatureMeta().signatureExpiresAt()).isEqualTo(1_900L);
    assertThat(draft.expiresAt()).isEqualTo(LocalDateTime.ofEpochSecond(1_300, 0, ZoneOffset.UTC));
    assertThat(draft.tokenMovement().fromRole()).isEqualTo("BUYER");
    assertThat(draft.tokenMovement().toRole()).isEqualTo("ESCROW");
    assertThat(draft.payloadSnapshotJson()).contains("\"signedAt\":1000");

    ArgumentCaptor<MarketplaceServerSigPreimage> preimageCaptor =
        ArgumentCaptor.forClass(MarketplaceServerSigPreimage.class);
    then(signMarketplaceServerSigPort).should().sign(preimageCaptor.capture());
    assertThat(preimageCaptor.getValue())
        .isInstanceOf(MarketplaceServerSigPreimage.PurchaseClassPreimage.class);
    then(buildMarketplaceEscrowCallDataPort)
        .should()
        .encode(
            eq(MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE),
            eq(ORDER_KEY),
            eq(TOKEN),
            eq(TRAINER),
            eq(BigInteger.valueOf(50_000)),
            eq(1_000L),
            any());
  }

  @Test
  @DisplayName("deadline refund draft는 서버 signature 없이 contract deadline cap을 적용하지 않는다")
  void build_deadlineRefund_omitsServerSignature() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER));
    givenDraftContext();
    given(
            buildMarketplaceEscrowCallDataPort.encode(
                any(), any(), any(), any(), any(), any(), any()))
        .willReturn("0xrefund");

    MarketplaceExecutionDraft draft =
        sut.build(
            request(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND,
                MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
                1_100L));

    assertThat(draft.signatureMeta()).isNull();
    assertThat(draft.expiresAt()).isEqualTo(LocalDateTime.ofEpochSecond(1_200, 0, ZoneOffset.UTC));
    then(signMarketplaceServerSigPort).shouldHaveNoInteractions();
    then(buildMarketplaceEscrowCallDataPort)
        .should()
        .encode(
            eq(MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND),
            eq(ORDER_KEY),
            eq(TOKEN),
            eq(TRAINER),
            eq(BigInteger.valueOf(50_000)),
            eq(null),
            eq(null));
  }

  @Test
  @DisplayName("confirm draft 만료는 contract deadline보다 늦게 노출되지 않는다")
  void build_confirmCapsExpiresAtToContractDeadline() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER));
    givenDraftContext();
    given(signMarketplaceServerSigPort.sign(any()))
        .willReturn(new MarketplaceServerSigResult(1_000L, SIGNATURE, SIGNING_INSTANT));
    given(
            buildMarketplaceEscrowCallDataPort.encode(
                any(), any(), any(), any(), any(), anyLong(), any()))
        .willReturn("0xcancel");

    MarketplaceExecutionDraft draft =
        sut.build(
            request(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM,
                MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
                1_100L));

    assertThat(draft.expiresAt()).isEqualTo(LocalDateTime.ofEpochSecond(1_100, 0, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("cancel draft 만료는 contract deadline으로 cap하지 않는다")
  void build_cancelDoesNotCapExpiresAtToContractDeadline() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER));
    givenDraftContext();
    given(signMarketplaceServerSigPort.sign(any()))
        .willReturn(new MarketplaceServerSigResult(1_000L, SIGNATURE, SIGNING_INSTANT));
    given(
            buildMarketplaceEscrowCallDataPort.encode(
                any(), any(), any(), any(), any(), anyLong(), any()))
        .willReturn("0xcancel");

    MarketplaceExecutionDraft draft =
        sut.build(
            request(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
                MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
                1_100L));

    assertThat(draft.expiresAt()).isEqualTo(LocalDateTime.ofEpochSecond(1_300, 0, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("approve-batch purchase는 별도 UX/정책이 없으므로 draft builder에서 명시적으로 차단한다")
  void build_approveBatchPurchase_isBlocked() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER));

    assertThatThrownBy(() -> sut.build(request(MarketplaceAllowanceStrategy.APPROVE_BATCH)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));
  }

  @Test
  @DisplayName("payload hash는 signedAt/signature/callData 같은 volatile calldata material을 제외한다")
  void build_payloadHash_excludesVolatileSignatureAndCallData() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER));
    givenDraftContext();
    given(signMarketplaceServerSigPort.sign(any()))
        .willReturn(new MarketplaceServerSigResult(1_000L, signature((byte) 1), SIGNING_INSTANT))
        .willReturn(
            new MarketplaceServerSigResult(
                1_001L, signature((byte) 2), SIGNING_INSTANT.plusSeconds(1)));
    given(
            buildMarketplaceEscrowCallDataPort.encode(
                any(), any(), any(), any(), any(), anyLong(), any()))
        .willReturn("0xcalldata1")
        .willReturn("0xcalldata2");

    MarketplaceExecutionDraft first =
        sut.build(request(MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE));
    MarketplaceExecutionDraft second =
        sut.build(request(MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE));

    assertThat(first.payloadHash()).isEqualTo(second.payloadHash());
    assertThat(first.payloadSnapshotJson()).contains("\"callData\":\"0xcalldata1\"");
    assertThat(second.payloadSnapshotJson()).contains("\"callData\":\"0xcalldata2\"");
    assertThat(first.payloadSnapshotJson()).isNotEqualTo(second.payloadSnapshotJson());
  }

  private MarketplaceEscrowExecutionRequest request(MarketplaceAllowanceStrategy strategy) {
    return request(MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, strategy);
  }

  private void givenDraftContext() {
    given(loadMarketplaceEip7702DraftContextPort.load(BUYER))
        .willReturn(
            new LoadMarketplaceEip7702DraftContextPort.MarketplaceEip7702DraftContext(
                10L, DELEGATE, 7L, "0xauthorizationHash", 300L));
  }

  private MarketplaceEscrowExecutionRequest request(
      MarketplaceExecutionActionType actionType, MarketplaceAllowanceStrategy strategy) {
    return request(actionType, strategy, 1_800L);
  }

  private MarketplaceEscrowExecutionRequest request(
      MarketplaceExecutionActionType actionType,
      MarketplaceAllowanceStrategy strategy,
      Long contractDeadlineEpochSeconds) {
    return new MarketplaceEscrowExecutionRequest(
        actionType,
        123L,
        "123",
        ORDER_ID,
        ORDER_KEY,
        MarketplaceActorType.BUYER,
        10L,
        10L,
        20L,
        10L,
        20L,
        3L,
        "PENDING",
        "LOCKED",
        BUYER,
        TRAINER,
        TOKEN,
        BigInteger.valueOf(50_000),
        strategy,
        50_000,
        LocalDateTime.of(2026, 5, 20, 11, 0),
        contractDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        "attempt-token",
        "PENDING");
  }

  private byte[] signature(byte fill) {
    byte[] bytes = new byte[65];
    java.util.Arrays.fill(bytes, fill);
    return bytes;
  }
}
