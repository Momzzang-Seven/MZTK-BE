package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import org.web3j.crypto.WalletUtils;

public record RegisterWalletCommand(
    Long userId, String walletAddress, String signature, String nonce) {
  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new IllegalArgumentException("Wallet address must not be blank");
    }
    if (signature == null || signature.isBlank()) {
      throw new IllegalArgumentException("Signature must not be blank");
    }
    if (nonce == null || nonce.isBlank()) {
      throw new IllegalArgumentException("Nonce must not be blank");
    }

    // Validate Ethereum address format
    if (!walletAddress.startsWith("0x") || !WalletUtils.isValidAddress(walletAddress)) {
      throw new IllegalArgumentException("Invalid Ethereum address format");
    }

    // Validate signature format (should be 132 chars: 0x + 130 hex)
    if (!signature.matches("^0x[0-9a-fA-F]{130}$")) {
      throw new IllegalArgumentException("Invalid signature format");
    }
  }
}
