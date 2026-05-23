package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for synchronizing wallet registration session state after approval tx submission. */
public record MarkWalletRegistrationApprovalSubmittedCommand(
    String registrationId, String executionIntentId, String submittedTransactionStatus) {

  public MarkWalletRegistrationApprovalSubmittedCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
  }
}
