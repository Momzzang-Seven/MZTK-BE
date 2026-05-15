package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for operator-owned local finalization retry after approval confirmation. */
public record RetryWalletRegistrationFinalizationCommand(String registrationId) {

  public RetryWalletRegistrationFinalizationCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
  }
}
