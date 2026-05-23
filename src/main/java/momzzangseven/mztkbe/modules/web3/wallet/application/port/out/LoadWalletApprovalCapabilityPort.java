package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;

public interface LoadWalletApprovalCapabilityPort {

  WalletApprovalCapability load();

  default void requireAvailable() {
    WalletApprovalCapability capability = load();
    if (!capability.available()) {
      throw new WalletApprovalUnavailableException(capability.reason());
    }
  }
}
