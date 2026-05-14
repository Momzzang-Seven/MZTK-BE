package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record WalletApprovalTtlPolicy(long minimumRemainingSeconds) {

  public WalletApprovalTtlPolicy {
    if (minimumRemainingSeconds <= 0) {
      throw new Web3InvalidInputException("minimumRemainingSeconds must be positive");
    }
  }
}
