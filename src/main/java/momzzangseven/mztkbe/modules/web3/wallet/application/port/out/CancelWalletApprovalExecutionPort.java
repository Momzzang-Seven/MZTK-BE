package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

public interface CancelWalletApprovalExecutionPort {

  boolean cancelIfSignable(String executionIntentId, String errorCode, String errorReason);
}
