package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

public record WalletApprovalCapability(boolean available, String reason) {

  public static WalletApprovalCapability enabled() {
    return new WalletApprovalCapability(true, null);
  }

  public static WalletApprovalCapability unavailable(String reason) {
    return new WalletApprovalCapability(false, reason);
  }
}
