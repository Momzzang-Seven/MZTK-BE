package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for finalizing a wallet registration after approval execution confirmation. */
public record FinalizeWalletRegistrationCommand(String registrationId, String executionIntentId) {

  public FinalizeWalletRegistrationCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
  }
}
