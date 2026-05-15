package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionSupport;

public interface LoadWalletApprovalExecutionSupportPort {

  WalletApprovalExecutionSupport load(String authorityAddress);
}
