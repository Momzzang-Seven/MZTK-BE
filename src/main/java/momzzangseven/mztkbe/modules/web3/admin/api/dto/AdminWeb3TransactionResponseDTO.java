package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;

@Builder
public record AdminWeb3TransactionResponseDTO(
    Long transactionId,
    String idempotencyKey,
    String referenceType,
    String referenceId,
    String txType,
    Long fromUserId,
    Long toUserId,
    String fromAddress,
    String toAddress,
    String status,
    String txHash,
    String failureReason,
    String processingBy,
    LocalDateTime processingUntil,
    LocalDateTime signedAt,
    LocalDateTime broadcastedAt,
    LocalDateTime confirmedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminWeb3TransactionResponseDTO from(AdminWeb3TransactionView view) {
    if (view == null) {
      throw new Web3InvalidInputException("view is required");
    }
    return AdminWeb3TransactionResponseDTO.builder()
        .transactionId(view.transactionId())
        .idempotencyKey(view.idempotencyKey())
        .referenceType(view.referenceType())
        .referenceId(view.referenceId())
        .txType(view.txType())
        .fromUserId(view.fromUserId())
        .toUserId(view.toUserId())
        .fromAddress(view.fromAddress())
        .toAddress(view.toAddress())
        .status(view.status())
        .txHash(view.txHash())
        .failureReason(view.failureReason())
        .processingBy(view.processingBy())
        .processingUntil(view.processingUntil())
        .signedAt(view.signedAt())
        .broadcastedAt(view.broadcastedAt())
        .confirmedAt(view.confirmedAt())
        .createdAt(view.createdAt())
        .updatedAt(view.updatedAt())
        .build();
  }
}
