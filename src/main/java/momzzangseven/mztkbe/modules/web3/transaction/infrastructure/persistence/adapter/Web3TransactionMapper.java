package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import org.springframework.stereotype.Component;

/** Mapper between web3 transaction domain model and persistence entity. */
@Component
public class Web3TransactionMapper {

  public Web3Transaction toDomain(Web3TransactionEntity entity) {
    return Web3Transaction.reconstitute(
        entity.getId(),
        entity.getIdempotencyKey(),
        entity.getReferenceType(),
        entity.getReferenceId(),
        entity.getFromUserId(),
        entity.getToUserId(),
        entity.getFromAddress(),
        entity.getToAddress(),
        entity.getAmountWei(),
        entity.getNonce(),
        entity.getStatus(),
        entity.getTxHash(),
        entity.getSignedAt(),
        entity.getBroadcastedAt(),
        entity.getConfirmedAt(),
        entity.getSignedRawTx(),
        entity.getFailureReason(),
        entity.getProcessingUntil(),
        entity.getProcessingBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public Web3TransactionEntity toEntity(Web3Transaction transaction) {
    return Web3TransactionEntity.builder()
        .id(transaction.getId())
        .idempotencyKey(transaction.getIdempotencyKey())
        .referenceType(transaction.getReferenceType())
        .referenceId(transaction.getReferenceId())
        .fromUserId(transaction.getFromUserId())
        .toUserId(transaction.getToUserId())
        .fromAddress(transaction.getFromAddress())
        .toAddress(transaction.getToAddress())
        .amountWei(transaction.getAmountWei())
        .nonce(transaction.getNonce())
        .status(transaction.getStatus())
        .txHash(transaction.getTxHash())
        .signedAt(transaction.getSignedAt())
        .broadcastedAt(transaction.getBroadcastedAt())
        .confirmedAt(transaction.getConfirmedAt())
        .signedRawTx(transaction.getSignedRawTx())
        .failureReason(transaction.getFailureReason())
        .processingUntil(transaction.getProcessingUntil())
        .processingBy(transaction.getProcessingBy())
        .createdAt(transaction.getCreatedAt())
        .updatedAt(transaction.getUpdatedAt())
        .build();
  }
}
