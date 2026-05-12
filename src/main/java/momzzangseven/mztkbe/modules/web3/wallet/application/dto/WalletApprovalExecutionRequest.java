package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record WalletApprovalExecutionRequest(
    String registrationId, Long requesterUserId, String walletAddress) {

  public WalletApprovalExecutionRequest {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new Web3InvalidInputException("walletAddress is required");
    }
  }
}
