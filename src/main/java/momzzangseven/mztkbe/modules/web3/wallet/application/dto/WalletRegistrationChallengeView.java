package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Wallet-owned challenge view used by wallet registration services. */
public record WalletRegistrationChallengeView(
    Long userId,
    String walletAddress,
    String nonce,
    String message,
    boolean used,
    boolean expired) {

  public boolean matchesUser(Long userId) {
    return this.userId != null && this.userId.equals(userId);
  }

  public boolean matchesAddress(String walletAddress) {
    return this.walletAddress != null
        && walletAddress != null
        && this.walletAddress.equalsIgnoreCase(walletAddress);
  }
}
