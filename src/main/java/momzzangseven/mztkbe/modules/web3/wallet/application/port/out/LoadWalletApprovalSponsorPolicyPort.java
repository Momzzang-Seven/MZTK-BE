package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;

/** Loads sponsor policy data needed by wallet approval availability checks. */
public interface LoadWalletApprovalSponsorPolicyPort {

  WalletApprovalSponsorPolicy load();
}
