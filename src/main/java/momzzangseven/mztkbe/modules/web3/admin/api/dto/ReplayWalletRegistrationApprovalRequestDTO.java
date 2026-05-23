package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalInputLimits;

public record ReplayWalletRegistrationApprovalRequestDTO(
    @Size(max = ReplayWalletRegistrationApprovalInputLimits.REGISTRATION_ID_MAX_LENGTH)
        String registrationId,
    Long transactionId,
    @Size(max = ReplayWalletRegistrationApprovalInputLimits.EXECUTION_INTENT_ID_MAX_LENGTH)
        String executionIntentId,
    @NotBlank @Size(max = ReplayWalletRegistrationApprovalInputLimits.REASON_MAX_LENGTH)
        String reason,
    @NotBlank @Size(max = ReplayWalletRegistrationApprovalInputLimits.EVIDENCE_MAX_LENGTH)
        String evidence) {

  public ReplayWalletRegistrationApprovalCommand toCommand(Long operatorId) {
    return new ReplayWalletRegistrationApprovalCommand(
        operatorId, registrationId, transactionId, executionIntentId, reason, evidence);
  }
}
