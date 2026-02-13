package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;
import org.web3j.crypto.WalletUtils;

/**
 * Command for unlinking a wallet
 *
 * <p>Data Transfer Object for wallet unlinking operations.
 */
public record UnlinkWalletCommand(Long userId, String walletAddress) {

  /**
   * Compact constructor for normalization
   *
   * <p>Converts wallet address to lowercase for consistency across the system.
   */
  public UnlinkWalletCommand {
    if (walletAddress != null) {
      walletAddress = walletAddress.toLowerCase();
    }
  }

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException(Web3ValidationMessage.USER_ID_POSITIVE);
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new Web3InvalidInputException("walletAddress must not be blank");
    }
    if (!isValidEthereumAddress(walletAddress)) {
      throw new Web3InvalidInputException("Invalid Ethereum address format");
    }
  }

  /**
   * Simple Ethereum address validation helper.
   *
   * <p>Conditions: 1. Starts with 0x 2. Must be 42 characters (including 0x) 3. Valid hex
   * characters after 0x 4. Must pass Web3j validation (format + checksum if applicable)
   */
  private boolean isValidEthereumAddress(String address) {
    if (!address.startsWith("0x")) {
      return false;
    }
    return WalletUtils.isValidAddress(address);
  }
}
