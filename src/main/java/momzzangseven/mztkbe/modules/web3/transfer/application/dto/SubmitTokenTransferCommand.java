package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record SubmitTokenTransferCommand(
    Long userId, String prepareId, String authorizationSignature, String executionSignature) {

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (prepareId == null || prepareId.isBlank()) {
      throw new Web3InvalidInputException("prepareId is required");
    }
    if (authorizationSignature == null || authorizationSignature.isBlank()) {
      throw new Web3InvalidInputException("authorizationSignature is required");
    }
    if (executionSignature == null || executionSignature.isBlank()) {
      throw new Web3InvalidInputException("executionSignature is required");
    }
  }
}
