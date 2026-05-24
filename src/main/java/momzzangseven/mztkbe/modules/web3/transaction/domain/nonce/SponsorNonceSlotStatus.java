package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

/** Application-level sponsor nonce slot state. */
public enum SponsorNonceSlotStatus {
  RESERVED,
  REPLACEMENT_PREPARING,
  SIGNED,
  BROADCASTING,
  BROADCASTED,
  CONSUMED,
  CONSUMED_UNKNOWN,
  STUCK,
  OPERATOR_REVIEW_REQUIRED,
  DROPPED;

  public boolean isOpenWindowCounted() {
    return switch (this) {
      case RESERVED, REPLACEMENT_PREPARING, SIGNED, BROADCASTING, BROADCASTED, STUCK -> true;
      case CONSUMED, CONSUMED_UNKNOWN, OPERATOR_REVIEW_REQUIRED, DROPPED -> false;
    };
  }

  public boolean isTerminal() {
    return this == CONSUMED || this == CONSUMED_UNKNOWN;
  }

  public boolean blocksIssuance() {
    return this == OPERATOR_REVIEW_REQUIRED;
  }

  public boolean canTransitionTo(SponsorNonceSlotStatus next) {
    if (next == null) {
      return false;
    }
    return switch (this) {
      case RESERVED -> next == SIGNED || next == DROPPED || next == OPERATOR_REVIEW_REQUIRED;
      case DROPPED -> next == RESERVED;
      case SIGNED -> next == BROADCASTING || next == OPERATOR_REVIEW_REQUIRED;
      case BROADCASTING ->
          next == BROADCASTED
              || next == CONSUMED
              || next == STUCK
              || next == OPERATOR_REVIEW_REQUIRED;
      case BROADCASTED ->
          next == CONSUMED
              || next == STUCK
              || next == CONSUMED_UNKNOWN
              || next == OPERATOR_REVIEW_REQUIRED;
      case CONSUMED_UNKNOWN -> next == CONSUMED;
      case STUCK ->
          next == CONSUMED || next == REPLACEMENT_PREPARING || next == OPERATOR_REVIEW_REQUIRED;
      case REPLACEMENT_PREPARING ->
          next == REPLACEMENT_PREPARING || next == SIGNED || next == OPERATOR_REVIEW_REQUIRED;
      case OPERATOR_REVIEW_REQUIRED ->
          next == CONSUMED || next == CONSUMED_UNKNOWN || next == STUCK || next == DROPPED;
      case CONSUMED -> false;
    };
  }
}
