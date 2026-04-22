package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaAdminExecutionAuthorityView(
    String currentServerSignerAddress,
    boolean relayerRegistered,
    boolean requiresUserSignature,
    String authorityModel) {

  public QnaAdminExecutionAuthorityView {
    if (currentServerSignerAddress == null || currentServerSignerAddress.isBlank()) {
      throw new Web3InvalidInputException("currentServerSignerAddress is required");
    }
    if (authorityModel == null || authorityModel.isBlank()) {
      throw new Web3InvalidInputException("authorityModel is required");
    }
  }
}
