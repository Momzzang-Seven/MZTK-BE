package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for mapping terminal approval execution states back to registration sessions. */
public record MarkWalletRegistrationApprovalTerminatedCommand(
    String registrationId,
    String executionIntentId,
    String terminalExecutionStatus,
    String failureReason) {

  public MarkWalletRegistrationApprovalTerminatedCommand {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (terminalExecutionStatus == null || terminalExecutionStatus.isBlank()) {
      throw new Web3InvalidInputException("terminalExecutionStatus is required");
    }
  }
}
