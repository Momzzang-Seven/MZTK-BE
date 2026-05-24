package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SponsorNonceSlotStatusTest {

  @Test
  void metadataHelpers_matchOpenWindowAndBlockingPolicy() {
    assertThat(openWindowStatuses())
        .containsExactlyInAnyOrder(
            SponsorNonceSlotStatus.RESERVED,
            SponsorNonceSlotStatus.REPLACEMENT_PREPARING,
            SponsorNonceSlotStatus.SIGNED,
            SponsorNonceSlotStatus.BROADCASTING,
            SponsorNonceSlotStatus.BROADCASTED,
            SponsorNonceSlotStatus.STUCK);

    assertThat(SponsorNonceSlotStatus.CONSUMED.isTerminal()).isTrue();
    assertThat(SponsorNonceSlotStatus.CONSUMED_UNKNOWN.isTerminal()).isTrue();
    assertThat(SponsorNonceSlotStatus.DROPPED.isTerminal()).isFalse();
    assertThat(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED.blocksIssuance()).isTrue();
  }

  @Test
  void canTransitionTo_allowsDocumentedTransitions() {
    assertAllowed(
        SponsorNonceSlotStatus.RESERVED,
        SponsorNonceSlotStatus.SIGNED,
        SponsorNonceSlotStatus.DROPPED,
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertAllowed(SponsorNonceSlotStatus.DROPPED, SponsorNonceSlotStatus.RESERVED);
    assertAllowed(
        SponsorNonceSlotStatus.SIGNED,
        SponsorNonceSlotStatus.BROADCASTING,
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertAllowed(
        SponsorNonceSlotStatus.BROADCASTING,
        SponsorNonceSlotStatus.BROADCASTED,
        SponsorNonceSlotStatus.CONSUMED,
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertAllowed(
        SponsorNonceSlotStatus.BROADCASTED,
        SponsorNonceSlotStatus.CONSUMED,
        SponsorNonceSlotStatus.STUCK,
        SponsorNonceSlotStatus.CONSUMED_UNKNOWN,
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertAllowed(SponsorNonceSlotStatus.CONSUMED_UNKNOWN, SponsorNonceSlotStatus.CONSUMED);
    assertAllowed(
        SponsorNonceSlotStatus.STUCK,
        SponsorNonceSlotStatus.REPLACEMENT_PREPARING,
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertAllowed(
        SponsorNonceSlotStatus.REPLACEMENT_PREPARING,
        SponsorNonceSlotStatus.REPLACEMENT_PREPARING,
        SponsorNonceSlotStatus.SIGNED,
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertAllowed(
        SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED,
        SponsorNonceSlotStatus.CONSUMED,
        SponsorNonceSlotStatus.CONSUMED_UNKNOWN,
        SponsorNonceSlotStatus.STUCK,
        SponsorNonceSlotStatus.DROPPED);
  }

  @Test
  void canTransitionTo_rejectsRepresentativeDisallowedTransitions() {
    assertThat(SponsorNonceSlotStatus.CONSUMED.canTransitionTo(SponsorNonceSlotStatus.RESERVED))
        .isFalse();
    assertThat(SponsorNonceSlotStatus.BROADCASTED.canTransitionTo(SponsorNonceSlotStatus.DROPPED))
        .isFalse();
    assertThat(SponsorNonceSlotStatus.BROADCASTING.canTransitionTo(SponsorNonceSlotStatus.SIGNED))
        .isFalse();
    assertThat(
            SponsorNonceSlotStatus.REPLACEMENT_PREPARING.canTransitionTo(
                SponsorNonceSlotStatus.STUCK))
        .isFalse();
    assertThat(SponsorNonceSlotStatus.RESERVED.canTransitionTo(SponsorNonceSlotStatus.RESERVED))
        .isFalse();
    assertThat(SponsorNonceSlotStatus.SIGNED.canTransitionTo(null)).isFalse();
  }

  private EnumSet<SponsorNonceSlotStatus> openWindowStatuses() {
    EnumSet<SponsorNonceSlotStatus> statuses = EnumSet.noneOf(SponsorNonceSlotStatus.class);
    for (SponsorNonceSlotStatus status : SponsorNonceSlotStatus.values()) {
      if (status.isOpenWindowCounted()) {
        statuses.add(status);
      }
    }
    return statuses;
  }

  private void assertAllowed(
      SponsorNonceSlotStatus from, SponsorNonceSlotStatus... allowedStatuses) {
    assertThat(allowedStatuses).allMatch(from::canTransitionTo);
  }
}
