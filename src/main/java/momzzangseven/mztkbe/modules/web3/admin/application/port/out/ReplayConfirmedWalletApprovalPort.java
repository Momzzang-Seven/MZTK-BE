package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

public interface ReplayConfirmedWalletApprovalPort {

  boolean replay(String executionIntentId, String expectedActionType);
}
