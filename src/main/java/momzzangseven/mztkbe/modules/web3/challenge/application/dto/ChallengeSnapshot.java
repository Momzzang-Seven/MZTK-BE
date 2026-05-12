package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;

/** Read-only challenge state exposed through challenge input ports. */
public record ChallengeSnapshot(
    Long userId,
    String walletAddress,
    String nonce,
    String message,
    boolean used,
    boolean expired) {

  public static ChallengeSnapshot from(Challenge challenge) {
    return new ChallengeSnapshot(
        challenge.getUserId(),
        challenge.getWalletAddress(),
        challenge.getNonce(),
        challenge.getMessage(),
        challenge.isUsed(),
        challenge.isExpired());
  }
}
