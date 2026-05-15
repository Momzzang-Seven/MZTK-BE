package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;

public interface SubmitWalletApprovalExecutionDraftPort {

  WalletApprovalExecutionIntentResult submit(WalletApprovalExecutionDraft draft);
}
