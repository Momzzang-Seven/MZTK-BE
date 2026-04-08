package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record GetTransferQuery(Long requesterUserId, String resourceId) {

  public GetTransferQuery {
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
  }
}
