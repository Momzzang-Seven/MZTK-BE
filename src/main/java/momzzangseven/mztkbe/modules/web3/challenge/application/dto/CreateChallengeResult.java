package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;

/**
 * Challenge creation result
 *
 * @param nonce unique challenge nonce
 * @param message EIP-4361 formatted message
 * @param expiresIn TTL in seconds
 */
public record CreateChallengeResult(String nonce, String message, int expiresIn) {

  /**
   * Create result from challenge and TTL
   *
   * @param challenge created challenge
   * @param ttlSeconds time-to-live in seconds
   * @return result with challenge data and TTL
   */
  public static CreateChallengeResult from(Challenge challenge, int ttlSeconds) {
    return new CreateChallengeResult(challenge.getNonce(), challenge.getMessage(), ttlSeconds);
  }
}
