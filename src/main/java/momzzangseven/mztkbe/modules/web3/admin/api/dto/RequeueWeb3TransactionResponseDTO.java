package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;

@Builder
public record RequeueWeb3TransactionResponseDTO(
    Long transactionId,
    String status,
    String previousStatus,
    String originalFailureReason,
    boolean requeued) {

  public static RequeueWeb3TransactionResponseDTO from(RequeueAdminWeb3TransactionResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return RequeueWeb3TransactionResponseDTO.builder()
        .transactionId(result.transactionId())
        .status(result.status())
        .previousStatus(result.previousStatus())
        .originalFailureReason(result.originalFailureReason())
        .requeued(result.requeued())
        .build();
  }
}
