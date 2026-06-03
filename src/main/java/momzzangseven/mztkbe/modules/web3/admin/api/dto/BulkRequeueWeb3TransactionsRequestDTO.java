package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsCommand;

public record BulkRequeueWeb3TransactionsRequestDTO(
    List<Long> transactionIds, @NotBlank String reason, @NotBlank String evidence) {

  public BulkRequeueAdminWeb3TransactionsCommand toCommand(Long operatorId) {
    return new BulkRequeueAdminWeb3TransactionsCommand(
        operatorId, transactionIds, reason, evidence);
  }
}
