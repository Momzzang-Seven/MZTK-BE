package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;

@Builder
public record TokenTransferPrepareResponseDTO(
    String prepareId,
    String idempotencyKey,
    String txType,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    LocalDateTime authExpiresAt,
    String payloadHashToSign) {

  public static TokenTransferPrepareResponseDTO from(PrepareTokenTransferResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return TokenTransferPrepareResponseDTO.builder()
        .prepareId(result.prepareId())
        .idempotencyKey(result.idempotencyKey())
        .txType(result.txType())
        .authorityAddress(result.authorityAddress())
        .authorityNonce(result.authorityNonce())
        .delegateTarget(result.delegateTarget())
        .authExpiresAt(result.authExpiresAt())
        .payloadHashToSign(result.payloadHashToSign())
        .build();
  }
}
