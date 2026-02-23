package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;

@Builder
public record MarkTransactionSucceededResponseDTO(
    Long transactionId, String previousStatus, String status, String txHash, String explorerUrl) {

  public static MarkTransactionSucceededResponseDTO from(MarkTransactionSucceededResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return MarkTransactionSucceededResponseDTO.builder()
        .transactionId(result.transactionId())
        .previousStatus(result.previousStatus())
        .status(result.status())
        .txHash(result.txHash())
        .explorerUrl(result.explorerUrl())
        .build();
  }
}
