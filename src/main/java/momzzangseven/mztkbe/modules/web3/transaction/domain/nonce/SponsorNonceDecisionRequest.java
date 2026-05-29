package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

import java.util.List;

/** Inputs required to decide the next sponsor nonce action. */
public record SponsorNonceDecisionRequest(
    long chainId,
    String fromAddress,
    long chainPendingNonce,
    long chainLatestNonce,
    Long mainPendingNonce,
    Long subPendingNonce,
    Long mainLatestNonce,
    Long subLatestNonce,
    int openWindowSize,
    List<SponsorNonceSlot> slots) {

  public SponsorNonceDecisionRequest {
    if (chainId <= 0) {
      throw new IllegalArgumentException("chainId must be positive");
    }
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new IllegalArgumentException("fromAddress is required");
    }
    if (chainPendingNonce < 0 || chainLatestNonce < 0) {
      throw new IllegalArgumentException("chain nonce must be >= 0");
    }
    if (openWindowSize <= 0) {
      throw new IllegalArgumentException("openWindowSize must be positive");
    }
    slots = slots == null ? List.of() : List.copyOf(slots);
  }

  public static SponsorNonceDecisionRequest of(
      long chainId,
      String fromAddress,
      long chainPendingNonce,
      long chainLatestNonce,
      int openWindowSize,
      List<SponsorNonceSlot> slots) {
    return new SponsorNonceDecisionRequest(
        chainId,
        fromAddress,
        chainPendingNonce,
        chainLatestNonce,
        null,
        null,
        null,
        null,
        openWindowSize,
        slots);
  }
}
