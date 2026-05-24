package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SponsorNonceDecisionServiceTest {
  private static final long CHAIN_ID = 84532L;
  private static final String SPONSOR = "0x" + "a".repeat(40);

  private final SponsorNonceDecisionService service = new SponsorNonceDecisionService();

  @Test
  void decide_whenNoOpenSlot_issuesChainPendingNonce() {
    SponsorNonceDecision decision = decide(51, 50);

    assertDecision(decision, SponsorNonceDecisionType.ISSUE_NONCE, 51L);
  }

  @Test
  void decide_whenOneOrTwoContiguousOpenSlots_issuesNextContiguousNonce() {
    SponsorNonceDecision oneOpen = decide(51, 50, slot(51, SponsorNonceSlotStatus.RESERVED));
    SponsorNonceDecision twoOpen =
        decide(
            51,
            50,
            slot(51, SponsorNonceSlotStatus.RESERVED),
            slot(52, SponsorNonceSlotStatus.SIGNED));

    assertDecision(oneOpen, SponsorNonceDecisionType.ISSUE_NONCE, 52L);
    assertDecision(twoOpen, SponsorNonceDecisionType.ISSUE_NONCE, 53L);
  }

  @Test
  void decide_whenThreeOpenSlots_waitsForOpenWindow() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slot(51, SponsorNonceSlotStatus.RESERVED),
            slot(52, SponsorNonceSlotStatus.SIGNED),
            slot(53, SponsorNonceSlotStatus.BROADCASTED));

    assertDecision(decision, SponsorNonceDecisionType.WAIT_FOR_OPEN_WINDOW, 51L);
  }

  @Test
  void decide_whenLowestSlotStuck_replacesLowestNonce() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slot(51, SponsorNonceSlotStatus.STUCK),
            slot(52, SponsorNonceSlotStatus.SIGNED));

    assertDecision(decision, SponsorNonceDecisionType.REPLACE_LOWEST_NONCE, 51L);
  }

  @Test
  void decide_whenReservedTimedOutWithoutChainEvidence_dropsUnbroadcastableReservation() {
    SponsorNonceDecision decision =
        decide(51, 50, slotBuilder(51, SponsorNonceSlotStatus.RESERVED).timedOut().build());

    assertDecision(decision, SponsorNonceDecisionType.DROP_UNBROADCASTABLE_RESERVATION, 51L);
  }

  @Test
  void decide_whenReservedTimedOutWithChainReachableEvidence_requiresOperatorReview() {
    SponsorNonceDecision decision =
        decide(51, 50, slotBuilder(51, SponsorNonceSlotStatus.RESERVED).timedOut().rawTx().build());

    assertDecision(decision, SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED, 51L);
  }

  @Test
  void decide_whenBroadcastedTimedOutAndLatestNotAdvancedAndEligible_replacesLowestNonce() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slotBuilder(51, SponsorNonceSlotStatus.BROADCASTED)
                .timedOut()
                .replacementEligible()
                .build());

    assertDecision(decision, SponsorNonceDecisionType.REPLACE_LOWEST_NONCE, 51L);
  }

  @Test
  void decide_whenBroadcastedTimedOutAndLatestAdvancedWithEvidence_consumesUnknownNonce() {
    SponsorNonceDecision decision =
        decide(
            52,
            52,
            slotBuilder(51, SponsorNonceSlotStatus.BROADCASTED)
                .timedOut()
                .retainedExternalEvidence()
                .build());

    assertDecision(decision, SponsorNonceDecisionType.CONSUME_UNKNOWN_NONCE, 51L);
  }

  @Test
  void decide_whenLatestAdvancedWithoutRetainedEvidence_requiresOperatorReview() {
    SponsorNonceDecision decision =
        decide(52, 52, slotBuilder(51, SponsorNonceSlotStatus.BROADCASTED).timedOut().build());

    assertDecision(decision, SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED, 51L);
  }

  @Test
  void decide_whenPendingAdvancedButLatestDidNot_doesNotTreatNonceAsConsumed() {
    SponsorNonceDecision decision =
        decide(
            52,
            51,
            slotBuilder(51, SponsorNonceSlotStatus.BROADCASTED)
                .timedOut()
                .replacementEligible()
                .build());

    assertDecision(decision, SponsorNonceDecisionType.REPLACE_LOWEST_NONCE, 51L);
  }

  @Test
  void decide_whenReceiptEvidenceExists_consumesKnownNonceBeforeOperatorReviewBlock() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slotBuilder(51, SponsorNonceSlotStatus.BROADCASTED).receiptEvidence().build(),
            slot(52, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED));

    assertDecision(decision, SponsorNonceDecisionType.CONSUME_KNOWN_NONCE, 51L);
  }

  @Test
  void decide_whenOperatorReviewExists_blocksIssuanceAndReplacement() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slot(51, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED),
            slot(52, SponsorNonceSlotStatus.STUCK));

    assertDecision(decision, SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED, 51L);
  }

  @Test
  void decide_whenDbSlotGapHasNoChainReachableHighSlot_repairsGap() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slot(51, SponsorNonceSlotStatus.RESERVED),
            slot(53, SponsorNonceSlotStatus.RESERVED));

    assertDecision(decision, SponsorNonceDecisionType.REPAIR_DB_SLOT_GAP, 52L);
  }

  @Test
  void decide_whenDbSlotGapHasSignedHighSlot_requiresOperatorReview() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slot(51, SponsorNonceSlotStatus.RESERVED),
            slotBuilder(53, SponsorNonceSlotStatus.SIGNED).rawTx().txHash().build());

    assertDecision(decision, SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED, 52L);
  }

  @Test
  void decide_whenRpcProvidersDisagreeOverOpenWindow_blocksIssuance() {
    SponsorNonceDecision decision =
        service.decide(
            new SponsorNonceDecisionRequest(
                CHAIN_ID, SPONSOR, 51, 50, 51L, 55L, 50L, 50L, 3, List.of()));

    assertDecision(decision, SponsorNonceDecisionType.RPC_DISAGREEMENT, null);
  }

  @Test
  void decide_whenChainPendingBelowLatest_blocksAsInvalidRpcSnapshot() {
    SponsorNonceDecision decision = decide(50, 51);

    assertDecision(decision, SponsorNonceDecisionType.RPC_DISAGREEMENT, null);
  }

  @Test
  void decide_whenDbOpenNonceAboveChainPending_repairsFromChainPendingNonce() {
    SponsorNonceDecision decision = decide(51, 50, slot(110, SponsorNonceSlotStatus.RESERVED));

    assertDecision(decision, SponsorNonceDecisionType.REPAIR_CHAIN_PENDING_GAP, 51L);
  }

  @Test
  void decide_whenDbCursorDriftIsLarge_repairsFromChainPendingNonce() {
    SponsorNonceDecision decision =
        decide(
            51,
            50,
            slot(110, SponsorNonceSlotStatus.RESERVED),
            slot(111, SponsorNonceSlotStatus.SIGNED),
            slot(112, SponsorNonceSlotStatus.BROADCASTED));

    assertDecision(decision, SponsorNonceDecisionType.REPAIR_CHAIN_PENDING_GAP, 51L);
  }

  @Test
  void decide_validatesSlotScope() {
    SponsorNonceSlot wrongChain =
        SponsorNonceSlot.builder(1L, SPONSOR, 51, SponsorNonceSlotStatus.RESERVED).build();

    assertThatThrownBy(() -> decide(51, 50, wrongChain))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slot chainId must match");
  }

  private SponsorNonceDecision decide(
      long chainPending, long chainLatest, SponsorNonceSlot... slots) {
    return service.decide(
        SponsorNonceDecisionRequest.of(
            CHAIN_ID, SPONSOR, chainPending, chainLatest, 3, List.of(slots)));
  }

  private SponsorNonceSlot slot(long nonce, SponsorNonceSlotStatus status) {
    return slotBuilder(nonce, status).build();
  }

  private SponsorNonceSlot.Builder slotBuilder(long nonce, SponsorNonceSlotStatus status) {
    return SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, nonce, status);
  }

  private void assertDecision(
      SponsorNonceDecision decision, SponsorNonceDecisionType type, Long nonce) {
    assertThat(decision.type()).isEqualTo(type);
    assertThat(decision.nonce()).isEqualTo(nonce);
  }
}
