package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for validating that an approval execution still belongs to the registration session. */
public record ValidateWalletRegistrationApprovalExecutionCommand(
    String registrationId, String executionIntentId, Long requesterUserId) {

  public ValidateWalletRegistrationApprovalExecutionCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
  }
}
