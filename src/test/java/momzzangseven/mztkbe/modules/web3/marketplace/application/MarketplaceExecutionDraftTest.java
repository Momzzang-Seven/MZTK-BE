package momzzangseven.mztkbe.modules.web3.marketplace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import org.junit.jupiter.api.Test;

class MarketplaceExecutionDraftTest {

  private static final String ORDER_ID = "123e4567-e89b-12d3-a456-426614174000";

  @Test
  void requestValidatesResourceIdAsReservationIdString() {
    MarketplaceEscrowExecutionRequest request = request();

    assertThat(request.resourceId()).isEqualTo("123");
    assertThat(request.orderId()).isEqualTo(ORDER_ID);
    assertThat(request.bookedPriceAmountKrw()).isEqualTo(50000);
  }

  @Test
  void requestRejectsResourceIdThatDoesNotMatchReservationId() {
    assertThatThrownBy(
            () ->
                new MarketplaceEscrowExecutionRequest(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                    123L,
                    "0xabc",
                    ORDER_ID,
                    7L,
                    7L,
                    9L,
                    3L,
                    "0xbuyer",
                    "0xtrainer",
                    50000,
                    expiresAt()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("resourceId");
  }

  @Test
  void requestRejectsInvalidOrderId() {
    assertThatThrownBy(
            () ->
                new MarketplaceEscrowExecutionRequest(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                    123L,
                    "123",
                    "not-a-uuid",
                    7L,
                    7L,
                    9L,
                    3L,
                    "0xbuyer",
                    "0xtrainer",
                    50000,
                    expiresAt()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("orderId");
  }

  @Test
  void draftPreservesResourceIdAndOrderIdSeparately() {
    MarketplaceExecutionDraft draft = eip7702Draft();

    assertThat(draft.resourceId()).isEqualTo("123");
    assertThat(draft.orderId()).isEqualTo(ORDER_ID);
  }

  @Test
  void draftAllowsEip7702AuthorityTupleWithoutUnsignedTxSnapshot() {
    MarketplaceExecutionDraft draft = eip7702Draft();

    assertThat(draft.authorityAddress()).isEqualTo("0xauthority");
    assertThat(draft.unsignedTxSnapshot()).isNull();
  }

  @Test
  void draftRejectsPartialAuthorityTuple() {
    assertThatThrownBy(() -> draft("0xauthority", 12L, null, "0xpayload", null, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authority tuple");
  }

  @Test
  void draftRequiresUnsignedTxSnapshotWhenAuthorityTupleIsAbsent() {
    assertThatThrownBy(() -> draft(null, null, null, null, null, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("unsignedTxSnapshot");
  }

  @Test
  void draftRequiresFingerprintWhenUnsignedTxSnapshotIsRequired() {
    assertThatThrownBy(() -> draft(null, null, null, null, unsignedTxSnapshot(), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("unsignedTxFingerprint");
  }

  @Test
  void draftRequiresFingerprintWhenFallbackSnapshotIsProvidedWithAuthorityTuple() {
    assertThatThrownBy(
            () -> draft("0xauthority", 12L, "0xdelegate", "0xpayload", unsignedTxSnapshot(), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("unsignedTxFingerprint");
  }

  @Test
  void draftRejectsNoAuthoritySnapshotPathWhenFallbackIsDisabled() {
    assertThatThrownBy(
            () -> draft(false, null, null, null, null, unsignedTxSnapshot(), "fingerprint-1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fallbackAllowed");
  }

  @Test
  void draftAcceptsFallbackUnsignedTxSnapshotPath() {
    MarketplaceExecutionDraft draft =
        draft(null, null, null, null, unsignedTxSnapshot(), "fingerprint-1");

    assertThat(draft.authorityAddress()).isNull();
    assertThat(draft.unsignedTxSnapshot()).isNotNull();
    assertThat(draft.unsignedTxFingerprint()).isEqualTo("fingerprint-1");
  }

  @Test
  void draftCallValidatesTargetValueAndData() {
    assertThatThrownBy(() -> new MarketplaceExecutionDraftCall(" ", BigInteger.ZERO, "0xdata"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("target");
    assertThatThrownBy(
            () -> new MarketplaceExecutionDraftCall("0xtarget", BigInteger.valueOf(-1), "0xdata"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("value");
    assertThatThrownBy(() -> new MarketplaceExecutionDraftCall("0xtarget", BigInteger.ZERO, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("data");
  }

  @Test
  void unsignedTxSnapshotValidatesOwnFields() {
    assertThatThrownBy(
            () ->
                new MarketplaceUnsignedTxSnapshot(
                    0L,
                    "0xfrom",
                    "0xto",
                    BigInteger.ZERO,
                    "0xdata",
                    1L,
                    BigInteger.valueOf(21_000),
                    BigInteger.ONE,
                    BigInteger.TWO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId");
  }

  @Test
  void unsignedTxSnapshotRejectsZeroMaxPriorityFeePerGas() {
    assertThatThrownBy(
            () ->
                new MarketplaceUnsignedTxSnapshot(
                    10L,
                    "0xfrom",
                    "0xto",
                    BigInteger.ZERO,
                    "0xdata",
                    1L,
                    BigInteger.valueOf(21_000),
                    BigInteger.ZERO,
                    BigInteger.TWO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas");
  }

  @Test
  void unsignedTxSnapshotRejectsMaxFeeBelowPriorityFee() {
    assertThatThrownBy(
            () ->
                new MarketplaceUnsignedTxSnapshot(
                    10L,
                    "0xfrom",
                    "0xto",
                    BigInteger.ZERO,
                    "0xdata",
                    1L,
                    BigInteger.valueOf(21_000),
                    BigInteger.TWO,
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas");
  }

  @Test
  void draftRejectsMissingRequiredFields() {
    assertThatThrownBy(
            () ->
                new MarketplaceExecutionDraft(
                    MarketplaceExecutionResourceType.ORDER,
                    " ",
                    MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                    7L,
                    9L,
                    ORDER_ID,
                    "root",
                    "0xpayload",
                    "{}",
                    calls(),
                    true,
                    "0xauthority",
                    12L,
                    "0xdelegate",
                    "0xpayload",
                    null,
                    null,
                    expiresAt()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("resourceId");
  }

  private static MarketplaceEscrowExecutionRequest request() {
    return new MarketplaceEscrowExecutionRequest(
        MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        123L,
        "123",
        ORDER_ID,
        7L,
        7L,
        9L,
        3L,
        "0xbuyer",
        "0xtrainer",
        50000,
        expiresAt());
  }

  private static MarketplaceExecutionDraft eip7702Draft() {
    return draft("0xauthority", 12L, "0xdelegate", "0xpayload", null, null);
  }

  private static MarketplaceExecutionDraft draft(
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      String authorizationPayloadHash,
      MarketplaceUnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint) {
    return draft(
        true,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authorizationPayloadHash,
        unsignedTxSnapshot,
        unsignedTxFingerprint);
  }

  private static MarketplaceExecutionDraft draft(
      boolean fallbackAllowed,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      String authorizationPayloadHash,
      MarketplaceUnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint) {
    return new MarketplaceExecutionDraft(
        MarketplaceExecutionResourceType.ORDER,
        "123",
        MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
        MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        7L,
        9L,
        ORDER_ID,
        "marketplace:marketplace_class_purchase:7:123",
        "0xpayload",
        "{\"reservationId\":123}",
        calls(),
        fallbackAllowed,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authorizationPayloadHash,
        unsignedTxSnapshot,
        unsignedTxFingerprint,
        expiresAt());
  }

  private static List<MarketplaceExecutionDraftCall> calls() {
    return List.of(new MarketplaceExecutionDraftCall("0xtarget", BigInteger.ZERO, "0xdata"));
  }

  private static MarketplaceUnsignedTxSnapshot unsignedTxSnapshot() {
    return new MarketplaceUnsignedTxSnapshot(
        10L,
        "0xfrom",
        "0xto",
        BigInteger.ZERO,
        "0xdata",
        1L,
        BigInteger.valueOf(21_000),
        BigInteger.ONE,
        BigInteger.TWO);
  }

  private static LocalDateTime expiresAt() {
    return LocalDateTime.parse("2026-05-20T10:05:00");
  }
}
