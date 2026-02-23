package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferTransactionPersistenceAdapter implements TransferTransactionPersistencePort {

  private final Web3TransactionJpaRepository repository;

  @Override
  public Optional<TransferTransaction> findByIdempotencyKey(String idempotencyKey) {
    return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
  }

  @Override
  public Optional<TransferTransaction> findById(Long transactionId) {
    return repository.findById(transactionId).map(this::toDomain);
  }

  @Override
  public TransferTransaction createAndFlush(TransferTransaction transaction) {
    Web3TransactionEntity entity = Web3TransactionEntity.builder().build();
    entity.setIdempotencyKey(transaction.getIdempotencyKey());
    entity.setReferenceType(transaction.getReferenceType());
    entity.setReferenceId(transaction.getReferenceId());
    entity.setFromUserId(transaction.getFromUserId());
    entity.setToUserId(transaction.getToUserId());
    entity.setFromAddress(transaction.getFromAddress());
    entity.setToAddress(transaction.getToAddress());
    entity.setAmountWei(transaction.getAmountWei());
    entity.setNonce(transaction.getNonce());
    entity.setTxType(transaction.getTxType());
    entity.setAuthorityAddress(transaction.getAuthorityAddress());
    entity.setAuthorizationNonce(transaction.getAuthorizationNonce());
    entity.setDelegateTarget(transaction.getDelegateTarget());
    entity.setAuthorizationExpiresAt(transaction.getAuthorizationExpiresAt());
    entity.setStatus(transaction.getStatus());
    entity.setTxHash(transaction.getTxHash());
    entity.setSignedAt(transaction.getSignedAt());
    entity.setBroadcastedAt(transaction.getBroadcastedAt());
    entity.setConfirmedAt(transaction.getConfirmedAt());
    entity.setSignedRawTx(transaction.getSignedRawTx());
    entity.setFailureReason(transaction.getFailureReason());
    entity.setProcessingUntil(transaction.getProcessingUntil());
    entity.setProcessingBy(transaction.getProcessingBy());

    return toDomain(repository.saveAndFlush(entity));
  }

  private TransferTransaction toDomain(Web3TransactionEntity entity) {
    return TransferTransaction.builder()
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
