package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

/** Result of a sponsor nonce slot decision. */
public record SponsorNonceDecision(SponsorNonceDecisionType type, Long nonce, String reason) {

  public SponsorNonceDecision {
    if (type == null) {
      throw new IllegalArgumentException("type is required");
    }
  }

  public static SponsorNonceDecision issue(long nonce) {
    return new SponsorNonceDecision(SponsorNonceDecisionType.ISSUE_NONCE, nonce, "ISSUE_NONCE");
  }

  public static SponsorNonceDecision of(SponsorNonceDecisionType type, Long nonce, String reason) {
    return new SponsorNonceDecision(type, nonce, reason);
  }

  public boolean issuesNonce() {
    return type == SponsorNonceDecisionType.ISSUE_NONCE;
  }
}
