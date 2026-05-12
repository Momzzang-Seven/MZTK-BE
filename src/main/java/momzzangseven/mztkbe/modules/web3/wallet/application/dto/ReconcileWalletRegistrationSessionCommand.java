package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for reconciling one wallet registration session from execution state. */
public record ReconcileWalletRegistrationSessionCommand(String registrationId) {

  public ReconcileWalletRegistrationSessionCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
  }
}
