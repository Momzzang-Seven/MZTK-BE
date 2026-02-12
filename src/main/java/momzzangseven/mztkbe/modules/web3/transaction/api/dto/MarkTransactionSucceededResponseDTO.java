package momzzangseven.mztkbe.modules.web3.transaction.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;

@Builder
public record MarkTransactionSucceededResponseDTO(
    Long transactionId, String previousStatus, String status, String txHash, String explorerUrl) {

  public static MarkTransactionSucceededResponseDTO from(MarkTransactionSucceededResult result) {
    return MarkTransactionSucceededResponseDTO.builder()
        .transactionId(result.transactionId())
        .previousStatus(result.previousStatus() != null ? result.previousStatus().name() : null)
        .status(result.status() != null ? result.status().name() : null)
        .txHash(result.txHash())
        .explorerUrl(result.explorerUrl())
        .build();
  }
}
