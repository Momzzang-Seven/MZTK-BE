package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import org.web3j.crypto.WalletUtils;

/**
 * Command for deactivating wallet
 *
 * <p>Uses walletAddress instead of walletId for better RESTful API design.
 *
 * <p>Wallet address is automatically normalized to lowercase on creation for consistency.
 */
public record DeactivateWalletCommand(Long userId, String walletAddress) {

  /**
   * Canonical constructor with wallet address normalization
   *
   * <p>Converts wallet address to lowercase for consistency across the system.
   */
  public DeactivateWalletCommand {
    // Normalize wallet address to lowercase if not null
    if (walletAddress != null) {
      walletAddress = walletAddress.toLowerCase();
    }
  }

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new IllegalArgumentException("Wallet address must not be blank");
    }
    // Validate Ethereum address format using Web3j
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
