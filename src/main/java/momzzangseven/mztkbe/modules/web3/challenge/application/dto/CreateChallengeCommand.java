package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import org.web3j.crypto.WalletUtils;

/**
 * Command for creating challenge.
 *
 * <p>Wallet address is automatically normalized to lowercase on creation for consistency.
 *
 * @param userId
 * @param purpose
 * @param walletAddress
 */
public record CreateChallengeCommand(Long userId, ChallengePurpose purpose, String walletAddress) {

  /**
   * Canonical constructor with wallet address normalization
   *
   * <p>Converts wallet address to lowercase for consistency across the system.
   */
  public CreateChallengeCommand {
    // Normalize wallet address to lowercase if not null
    if (walletAddress != null) {
      walletAddress = walletAddress.toLowerCase();
    }
  }

  /** Validation method */
  public void validate() {
    if (userId == null || userId <= 0) {
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

  /**
   * Validate Ethereum address format
   *
   * <p>Requirements: - Must start with "0x" - Must be exactly 42 characters (0x + 40 hex chars) -
   * Must pass Web3j validation (format + checksum if applicable)
   */
  private boolean isValidEthereumAddress(String address) {
    // 1. Check 0x prefix
    if (!address.startsWith("0x")) {
      return false;
    }

    // 2. Check Web3j validation (format + checksum)
    return WalletUtils.isValidAddress(address);
  }
}
