package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;
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
    if (walletAddress != null) {
      walletAddress = walletAddress.toLowerCase();
    }
  }

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException(Web3ValidationMessage.USER_ID_POSITIVE);
    }
    if (purpose == null) {
      throw new Web3InvalidInputException("purpose must not be null");
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
