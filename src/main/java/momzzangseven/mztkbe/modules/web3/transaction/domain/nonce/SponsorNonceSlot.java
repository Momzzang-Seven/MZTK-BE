package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

/** Snapshot of a sponsor-owned nonce slot used by the decision service. */
public record SponsorNonceSlot(
    long chainId,
    String fromAddress,
    long nonce,
    SponsorNonceSlotStatus status,
    boolean timedOut,
    boolean replacementEligible,
    boolean hasRawTx,
    boolean hasTxHash,
    boolean hasSigningEvidence,
    boolean hasBroadcastEvidence,
    boolean hasReceiptEvidence,
    boolean hasRetainedExternalEvidence) {

  public SponsorNonceSlot {
    if (chainId <= 0) {
      throw new IllegalArgumentException("chainId must be positive");
    }
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new IllegalArgumentException("fromAddress is required");
    }
    if (nonce < 0) {
      throw new IllegalArgumentException("nonce must be >= 0");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
  }

  public static Builder builder(
      long chainId, String fromAddress, long nonce, SponsorNonceSlotStatus status) {
    return new Builder(chainId, fromAddress, nonce, status);
  }

  boolean hasAnyChainReachableEvidence() {
    return hasRawTx || hasTxHash || hasSigningEvidence || hasBroadcastEvidence;
  }

  public static final class Builder {
    private final long chainId;
    private final String fromAddress;
    private final long nonce;
    private final SponsorNonceSlotStatus status;
    private boolean timedOut;
    private boolean replacementEligible;
    private boolean hasRawTx;
    private boolean hasTxHash;
    private boolean hasSigningEvidence;
    private boolean hasBroadcastEvidence;
    private boolean hasReceiptEvidence;
    private boolean hasRetainedExternalEvidence;

    private Builder(long chainId, String fromAddress, long nonce, SponsorNonceSlotStatus status) {
      this.chainId = chainId;
      this.fromAddress = fromAddress;
      this.nonce = nonce;
      this.status = status;
    }

    public Builder timedOut() {
      this.timedOut = true;
      return this;
    }

    public Builder replacementEligible() {
      this.replacementEligible = true;
      return this;
    }

    public Builder rawTx() {
      this.hasRawTx = true;
      return this;
    }

    public Builder txHash() {
      this.hasTxHash = true;
      return this;
    }

    public Builder signingEvidence() {
      this.hasSigningEvidence = true;
      return this;
    }

    public Builder broadcastEvidence() {
      this.hasBroadcastEvidence = true;
      return this;
    }

    public Builder receiptEvidence() {
      this.hasReceiptEvidence = true;
      return this;
    }

    public Builder retainedExternalEvidence() {
      this.hasRetainedExternalEvidence = true;
      return this;
    }

    public SponsorNonceSlot build() {
      return new SponsorNonceSlot(
          chainId,
          fromAddress,
          nonce,
          status,
          timedOut,
          replacementEligible,
          hasRawTx,
          hasTxHash,
          hasSigningEvidence,
          hasBroadcastEvidence,
          hasReceiptEvidence,
          hasRetainedExternalEvidence);
    }
  }
}
