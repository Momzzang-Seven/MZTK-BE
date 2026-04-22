package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;

public record QnaAdminExecutionAuthorityView(
    ExecutionSignerCapabilityView serverSigner,
    boolean relayerRegistered,
    QnaAdminRelayerRegistrationStatus relayerRegistrationStatus,
    boolean requiresUserSignature,
    String authorityModel) {

  public QnaAdminExecutionAuthorityView {
    if (serverSigner == null) {
      throw new Web3InvalidInputException("serverSigner is required");
    }
    if (relayerRegistrationStatus == null) {
      throw new Web3InvalidInputException("relayerRegistrationStatus is required");
    }
    if (authorityModel == null || authorityModel.isBlank()) {
      throw new Web3InvalidInputException("authorityModel is required");
    }
    if (serverSigner.signable()
        && (serverSigner.signerAddress() == null || serverSigner.signerAddress().isBlank())) {
      throw new Web3InvalidInputException("signerAddress is required when serverSigner is ready");
    }
    if (relayerRegistered
        && relayerRegistrationStatus != QnaAdminRelayerRegistrationStatus.REGISTERED) {
      throw new Web3InvalidInputException(
          "relayerRegistrationStatus must be REGISTERED when relayerRegistered is true");
    }
    if (!relayerRegistered
        && relayerRegistrationStatus == QnaAdminRelayerRegistrationStatus.REGISTERED) {
      throw new Web3InvalidInputException(
          "relayerRegistered must be true when relayerRegistrationStatus is REGISTERED");
    }
    if (!serverSigner.signable()
        && relayerRegistrationStatus != QnaAdminRelayerRegistrationStatus.UNCHECKED) {
      throw new Web3InvalidInputException(
          "relayerRegistrationStatus must be UNCHECKED when serverSigner is unavailable");
    }
    if (serverSigner.signable()
        && relayerRegistrationStatus == QnaAdminRelayerRegistrationStatus.UNCHECKED) {
      throw new Web3InvalidInputException(
          "relayerRegistrationStatus must be checked when serverSigner is available");
    }
  }
}
