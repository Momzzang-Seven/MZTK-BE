package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;

/**
 * Command for creating challenge.
 *
 * @param userId
 * @param purpose
 * @param walletAddress
 */
public record CreateChallengeCommand(Long userId, ChallengePurpose purpose, String walletAddress) {
  /** Validation method */
  public void validate() {
    if (userId == null || userId < 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    if (purpose == null) {
      throw new IllegalArgumentException("Purpose must not be null");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new IllegalArgumentException("Wallet address must not be blank");
    }
    if (!isValidEthereumAddress(walletAddress)) {
      throw new IllegalArgumentException("Invalid Ethereum address format");
    }
  }

  /** Validate the given wallet address is valid or not. */
  private boolean isValidEthereumAddress(String address) {
    return isValidEthereumAddress(address);
  }
}
