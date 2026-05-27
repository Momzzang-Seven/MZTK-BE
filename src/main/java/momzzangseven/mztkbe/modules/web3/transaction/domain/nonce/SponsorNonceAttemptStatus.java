package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

public enum SponsorNonceAttemptStatus {
  RESERVED,
  REPLACEMENT_PREPARING,
  SIGNED,
  BROADCASTING,
  BROADCASTED,
  CONSUMED,
  CONSUMED_UNKNOWN,
  STUCK,
  OPERATOR_REVIEW_REQUIRED,
  DROPPED,
  SUPERSEDED,
  ABANDONED;

  public boolean isTerminalHistoryStatus() {
    return switch (this) {
      case CONSUMED, CONSUMED_UNKNOWN, DROPPED, SUPERSEDED, ABANDONED -> true;
      default -> false;
    };
  }

  public static SponsorNonceAttemptStatus fromSlotStatus(SponsorNonceSlotStatus slotStatus) {
    if (slotStatus == null) {
      throw new IllegalArgumentException("slotStatus is required");
    }
    return SponsorNonceAttemptStatus.valueOf(slotStatus.name());
  }
}
