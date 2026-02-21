package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.TransferTransactionRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferTransactionPersistenceAdapter implements TransferTransactionPersistencePort {

  private final Web3TransactionJpaRepository repository;

  @Override
  public Optional<TransferTransactionRecord> findByIdempotencyKey(String idempotencyKey) {
    return repository.findByIdempotencyKey(idempotencyKey).map(this::toRecord);
  }

  @Override
  public Optional<TransferTransactionRecord> findById(Long transactionId) {
    return repository.findById(transactionId).map(this::toRecord);
  }

  @Override
  public TransferTransactionRecord saveAndFlush(TransferTransactionRecord record) {
    Web3TransactionEntity entity =
        record.getId() == null
            ? Web3TransactionEntity.builder().build()
            : repository.findById(record.getId()).orElseGet(Web3TransactionEntity.builder()::build);

    entity.setIdempotencyKey(record.getIdempotencyKey());
    entity.setReferenceType(record.getReferenceType());
    entity.setReferenceId(record.getReferenceId());
    entity.setFromUserId(record.getFromUserId());
    entity.setToUserId(record.getToUserId());
    entity.setFromAddress(record.getFromAddress());
    entity.setToAddress(record.getToAddress());
    entity.setAmountWei(record.getAmountWei());
    entity.setNonce(record.getNonce());
    entity.setTxType(record.getTxType());
    entity.setAuthorityAddress(record.getAuthorityAddress());
    entity.setAuthorizationNonce(record.getAuthorizationNonce());
    entity.setDelegateTarget(record.getDelegateTarget());
    entity.setAuthorizationExpiresAt(record.getAuthorizationExpiresAt());
    entity.setStatus(record.getStatus());
    entity.setTxHash(record.getTxHash());
    entity.setSignedAt(record.getSignedAt());
    entity.setBroadcastedAt(record.getBroadcastedAt());
    entity.setConfirmedAt(record.getConfirmedAt());
    entity.setSignedRawTx(record.getSignedRawTx());
    entity.setFailureReason(record.getFailureReason());
    entity.setProcessingUntil(record.getProcessingUntil());
    entity.setProcessingBy(record.getProcessingBy());

    return toRecord(repository.saveAndFlush(entity));
  }

  private TransferTransactionRecord toRecord(Web3TransactionEntity entity) {
    return TransferTransactionRecord.builder()
        .id(entity.getId())
        .idempotencyKey(entity.getIdempotencyKey())
        .referenceType(entity.getReferenceType())
        .referenceId(entity.getReferenceId())
        .fromUserId(entity.getFromUserId())
        .toUserId(entity.getToUserId())
        .fromAddress(entity.getFromAddress())
        .toAddress(entity.getToAddress())
        .amountWei(entity.getAmountWei())
        .nonce(entity.getNonce())
        .txType(entity.getTxType())
        .authorityAddress(entity.getAuthorityAddress())
        .authorizationNonce(entity.getAuthorizationNonce())
        .delegateTarget(entity.getDelegateTarget())
        .authorizationExpiresAt(entity.getAuthorizationExpiresAt())
        .status(entity.getStatus())
        .txHash(entity.getTxHash())
        .signedAt(entity.getSignedAt())
        .broadcastedAt(entity.getBroadcastedAt())
        .confirmedAt(entity.getConfirmedAt())
        .signedRawTx(entity.getSignedRawTx())
        .failureReason(entity.getFailureReason())
        .processingUntil(entity.getProcessingUntil())
        .processingBy(entity.getProcessingBy())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
