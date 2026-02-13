package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder
public record MarkTransactionSucceededResponseDTO(
    Long transactionId, String previousStatus, String status, String txHash, String explorerUrl) {

  public static MarkTransactionSucceededResponseDTO from(MarkTransactionSucceededResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return MarkTransactionSucceededResponseDTO.builder()
        .transactionId(result.transactionId())
        .previousStatus(toName(result.previousStatus()))
        .status(toName(result.status()))
        .txHash(result.txHash())
        .explorerUrl(result.explorerUrl())
        .build();
  }

  private static String toName(Web3TxStatus status) {
    return status == null ? null : status.name();
  }
}
