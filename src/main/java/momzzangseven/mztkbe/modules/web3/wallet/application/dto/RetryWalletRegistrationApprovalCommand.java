package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Owner-bound command for creating or reusing a retry approval intent. */
public record RetryWalletRegistrationApprovalCommand(Long requesterUserId, String registrationId) {

  public RetryWalletRegistrationApprovalCommand {
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
  }
}
