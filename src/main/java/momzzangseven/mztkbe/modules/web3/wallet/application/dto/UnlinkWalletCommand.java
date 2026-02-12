package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;
import org.web3j.crypto.WalletUtils;

/**
 * Command for unlinking wallet
 *
 * <p>Uses walletAddress instead of walletId for better RESTful API design.
 *
 * <p>Wallet address is automatically normalized to lowercase on creation for consistency.
 */
public record UnlinkWalletCommand(Long userId, String walletAddress) {

  /**
   * Canonical constructor with wallet address normalization
   *
   * <p>Converts wallet address to lowercase for consistency across the system.
   */
  public UnlinkWalletCommand {
    if (walletAddress != null) {
      walletAddress = walletAddress.toLowerCase();
    }
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
   * Validate Ethereum address format
   *
   * <p>Requirements: - Must start with "0x" - Must be exactly 42 characters (0x + 40 hex chars) -
   * Must pass Web3j validation (format + checksum if applicable)
   */
  private boolean isValidEthereumAddress(String address) {
    if (!address.startsWith("0x")) {
      return false;
    }
    return WalletUtils.isValidAddress(address);
  }
}
