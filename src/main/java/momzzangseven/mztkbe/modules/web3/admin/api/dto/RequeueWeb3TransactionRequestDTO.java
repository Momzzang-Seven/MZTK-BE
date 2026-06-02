package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionCommand;

public record RequeueWeb3TransactionRequestDTO(@NotBlank String reason, @NotBlank String evidence) {

  public RequeueAdminWeb3TransactionCommand toCommand(Long operatorId, Long transactionId) {
    return new RequeueAdminWeb3TransactionCommand(operatorId, transactionId, reason, evidence);
  }
}
