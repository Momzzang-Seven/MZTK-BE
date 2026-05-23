package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Owner-agnostic query for admin/support wallet registration recovery. */
public record LoadWalletRegistrationRecoveryStateQuery(String registrationId) {

  public LoadWalletRegistrationRecoveryStateQuery {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
  }
}
