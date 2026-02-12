package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionWorkPersistenceAdapter
    implements LoadTransactionWorkPort, UpdateTransactionPort {

  private final EntityManager entityManager;
  private final Web3TransactionJpaRepository repository;

  @Override
  @Transactional
  @SuppressWarnings("unchecked")
  public List<TransactionWorkItem> claimByStatus(
      Web3TxStatus status, int limit, String workerId, Duration claimTtl) {
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }
    if (workerId == null || workerId.isBlank()) {
      throw new IllegalArgumentException("workerId is required");
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime processingUntil = now.plus(claimTtl == null ? Duration.ofMinutes(2) : claimTtl);

    List<Number> ids =
        entityManager
            .createNativeQuery(
                """
                SELECT id
                FROM web3_transactions
                WHERE status = :status
                  AND (processing_until IS NULL OR processing_until < NOW())
                ORDER BY id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
            .setParameter("status", status.name())
            .setParameter("limit", limit)
            .getResultList();

    if (ids.isEmpty()) {
      return List.of();
    }

    List<Long> longIds = ids.stream().map(Number::longValue).toList();

    entityManager
        .createQuery(
            "update Web3TransactionEntity t set t.processingBy = :workerId,"
                + " t.processingUntil = :processingUntil where t.id in :ids")
        .setParameter("workerId", workerId)
        .setParameter("processingUntil", processingUntil)
        .setParameter("ids", longIds)
        .executeUpdate();

    Map<Long, Web3TransactionEntity> entityById =
        repository.findByIdIn(longIds).stream()
            .collect(Collectors.toMap(Web3TransactionEntity::getId, Function.identity()));

    return longIds.stream()
        .map(entityById::get)
        .filter(java.util.Objects::nonNull)
        .map(this::toWorkItem)
        .toList();
  }

  @Override
  @Transactional
  public void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash) {
    if (signedRawTx == null || signedRawTx.isBlank()) {
      throw new IllegalArgumentException("signedRawTx is required");
    }

    Web3TransactionEntity entity = load(transactionId);
    entity.setStatus(Web3TxStatus.SIGNED);
    entity.setNonce(nonce);
    entity.setSignedRawTx(signedRawTx);
    if (txHash != null && !txHash.isBlank()) {
      entity.setTxHash(txHash);
    }
    entity.setFailureReason(null);
    if (entity.getSignedAt() == null) {
      entity.setSignedAt(LocalDateTime.now());
    }
    entity.setProcessingBy(null);
    entity.setProcessingUntil(null);
  }

  @Override
  @Transactional
  public void markPending(Long transactionId, String txHash) {
    if (txHash == null || txHash.isBlank()) {
      throw new IllegalArgumentException("txHash is required");
    }

    Web3TransactionEntity entity = load(transactionId);
    entity.setStatus(Web3TxStatus.PENDING);
    entity.setTxHash(txHash);
    entity.setFailureReason(null);
    if (entity.getBroadcastedAt() == null) {
      entity.setBroadcastedAt(LocalDateTime.now());
    }
    entity.setProcessingBy(null);
    entity.setProcessingUntil(null);
  }

  @Override
  @Transactional
  public void updateStatus(
      Long transactionId, Web3TxStatus status, String txHash, String failureReason) {
    Web3TransactionEntity entity = load(transactionId);
    entity.setStatus(status);
    if (txHash != null && !txHash.isBlank()) {
      entity.setTxHash(txHash);
    }
    entity.setFailureReason(failureReason);

    LocalDateTime now = LocalDateTime.now();
    if (status == Web3TxStatus.SIGNED && entity.getSignedAt() == null) {
      entity.setSignedAt(now);
    }
    if (status == Web3TxStatus.PENDING && entity.getBroadcastedAt() == null) {
      entity.setBroadcastedAt(now);
    }
    if ((status == Web3TxStatus.SUCCEEDED || status == Web3TxStatus.FAILED_ONCHAIN)
        && entity.getConfirmedAt() == null) {
      entity.setConfirmedAt(now);
    }

    entity.setProcessingBy(null);
    entity.setProcessingUntil(null);
  }

  @Override
  @Transactional
  public void scheduleRetry(
      Long transactionId, String failureReason, LocalDateTime processingUntil) {
    Web3TransactionEntity entity = load(transactionId);
    entity.setFailureReason(failureReason);
    entity.setProcessingBy(null);
    entity.setProcessingUntil(processingUntil);
  }

  @Override
  @Transactional
  public void clearProcessingLock(Long transactionId) {
    Web3TransactionEntity entity = load(transactionId);
    entity.setProcessingBy(null);
    entity.setProcessingUntil(null);
  }

  private TransactionWorkItem toWorkItem(Web3TransactionEntity entity) {
    return new TransactionWorkItem(
        entity.getId(),
        entity.getFromAddress(),
        entity.getToAddress(),
        entity.getAmountWei(),
        entity.getTxHash(),
        entity.getSignedRawTx(),
        entity.getFailureReason());
  }

  private Web3TransactionEntity load(Long transactionId) {
    return repository
        .findById(transactionId)
        .orElseThrow(
            () -> new IllegalStateException("web3 transaction not found: id=" + transactionId));
  }
}
