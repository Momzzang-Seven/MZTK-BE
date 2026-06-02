package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetAdminWeb3TransactionsQuery;

public record GetAdminWeb3TransactionsRequestDTO(
    String failureReason,
    String status,
    String referenceType,
    String referenceId,
    String txType,
    @Min(0) Integer page,
    @Min(1) @Max(100) Integer size) {

  public GetAdminWeb3TransactionsQuery toQuery(Long operatorId) {
    return new GetAdminWeb3TransactionsQuery(
        operatorId, failureReason, status, referenceType, referenceId, txType, page, size);
  }
}
