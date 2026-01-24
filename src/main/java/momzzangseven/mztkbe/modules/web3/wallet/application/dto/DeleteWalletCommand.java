package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

public record DeleteWalletCommand(Long userId, Long walletId) {
  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    if (walletId == null || walletId <= 0) {
      throw new IllegalArgumentException("Wallet ID must be positive");
    }
  }
}
