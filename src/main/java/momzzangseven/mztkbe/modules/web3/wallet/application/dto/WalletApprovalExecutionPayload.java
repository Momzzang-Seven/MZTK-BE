package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionActionType;

public record WalletApprovalExecutionPayload(
    WalletApprovalExecutionActionType actionType,
    String registrationId,
    Long requesterUserId,
    String walletAddress,
    String tokenAddress,
    List<ApprovalCall> approvals) {

  public record ApprovalCall(
      String spender, BigInteger amountWei, String callTarget, String callData) {}
}
