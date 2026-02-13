package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;

@Builder
public record TokenTransferSubmitResponseDTO(Long transactionId, String status, String txHash) {

  public static TokenTransferSubmitResponseDTO from(SubmitTokenTransferResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return TokenTransferSubmitResponseDTO.builder()
        .transactionId(result.transactionId())
        .status(result.status())
        .txHash(result.txHash())
        .build();
  }
}
