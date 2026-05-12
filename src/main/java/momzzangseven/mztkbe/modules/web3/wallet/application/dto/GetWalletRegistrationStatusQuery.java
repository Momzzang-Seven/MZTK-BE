package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Owner-bound query for reading a wallet registration session status. */
public record GetWalletRegistrationStatusQuery(Long requesterUserId, String registrationId) {

  public GetWalletRegistrationStatusQuery {
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
  }
}
