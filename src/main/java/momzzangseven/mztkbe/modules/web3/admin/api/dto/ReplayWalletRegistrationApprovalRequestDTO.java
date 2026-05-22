package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalCommand;

public record ReplayWalletRegistrationApprovalRequestDTO(
    String registrationId,
    Long transactionId,
    String executionIntentId,
    @NotBlank String reason,
    @NotBlank String evidence) {

  public ReplayWalletRegistrationApprovalCommand toCommand(Long operatorId) {
    return new ReplayWalletRegistrationApprovalCommand(
        operatorId, registrationId, transactionId, executionIntentId, reason, evidence);
  }
}
