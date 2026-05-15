package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for expiring a pre-submission wallet registration session. */
public record ExpireWalletRegistrationSessionCommand(String registrationId) {

  public ExpireWalletRegistrationSessionCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
  }
}
