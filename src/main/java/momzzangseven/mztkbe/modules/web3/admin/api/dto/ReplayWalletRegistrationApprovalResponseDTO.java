package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalResult;

public record ReplayWalletRegistrationApprovalResponseDTO(
    String outcome,
    boolean replayInvoked,
    String registrationId,
    Long transactionId,
    String executionIntentId,
    String executionIntentStatus,
    String transactionStatus,
    String walletRegistrationStatus,
    boolean newerWalletRegistrationExists,
    String walletLastErrorCode,
    String walletLastErrorReason) {

  public static ReplayWalletRegistrationApprovalResponseDTO from(
      ReplayWalletRegistrationApprovalResult result) {
    return new ReplayWalletRegistrationApprovalResponseDTO(
        result.outcome(),
        result.replayInvoked(),
        result.registrationId(),
        result.transactionId(),
        result.executionIntentId(),
        result.executionIntentStatus(),
        result.transactionStatus(),
        result.walletRegistrationStatus(),
        result.newerWalletRegistrationExists(),
        result.walletLastErrorCode(),
        result.walletLastErrorReason());
  }
}
