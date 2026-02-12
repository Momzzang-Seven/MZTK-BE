package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.crypto.WalletUtils;

/**
 * Command for wallet registration
 *
 * <p>Wallet address is automatically normalized to lowercase on creation for consistency.
 */
public record RegisterWalletCommand(
    Long userId, String walletAddress, String signature, String nonce) {

  /**
   * Canonical constructor with wallet address normalization
   *
   * <p>Converts wallet address to lowercase for consistency across the system.
   */
  public RegisterWalletCommand {
    if (walletAddress != null) {
      walletAddress = walletAddress.toLowerCase();
    }
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new Web3InvalidInputException("walletAddress must not be blank");
    }
    if (signature == null || signature.isBlank()) {
      throw new Web3InvalidInputException("signature must not be blank");
    }
    if (nonce == null || nonce.isBlank()) {
      throw new Web3InvalidInputException("nonce must not be blank");
    }

    if (!walletAddress.startsWith("0x") || !WalletUtils.isValidAddress(walletAddress)) {
      throw new Web3InvalidInputException("Invalid Ethereum address format");
    }

    if (!signature.matches("^0x[0-9a-fA-F]{130}$")) {
      throw new Web3InvalidInputException("Invalid signature format");
    }
  }
}
