package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionWorkPersistenceAdapter
    implements LoadTransactionWorkPort, LoadTransactionPort, UpdateTransactionPort {

  private static final String NON_RETRYABLE_FAILURE_REASON_SQL =
      Arrays.stream(Web3TxFailureReason.values())
          .filter(reason -> !reason.isRetryable())
          .map(Web3TxFailureReason::code)
          .map(code -> "'" + code + "'")
          .collect(Collectors.joining(","));

  private static final String CLAIM_CREATED_SQL =
      """
      SELECT id
      FROM web3_transactions
      WHERE status = :status
        AND (processing_until IS NULL OR processing_until < NOW())
        AND (
            failure_reason IS NULL
            OR failure_reason NOT IN (%s)
        )
      ORDER BY id
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """
          .formatted(NON_RETRYABLE_FAILURE_REASON_SQL);

  private final EntityManager entityManager;
  private final Web3TransactionJpaRepository repository;

  @Override
  @Transactional
  @SuppressWarnings("unchecked")
  public List<TransactionWorkItem> claimByStatus(
      Web3TxStatus status, int limit, String workerId, Duration claimTtl) {
    if (status == null) {
      throw new Web3InvalidInputException("status is required");
    }
    if (limit <= 0) {
      throw new Web3InvalidInputException("limit must be > 0");
    }
    if (workerId == null || workerId.isBlank()) {
      throw new Web3InvalidInputException("workerId is required");
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime processingUntil = now.plus(claimTtl == null ? Duration.ofMinutes(2) : claimTtl);

    List<Number> ids = selectClaimableIds(status, limit);

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
  public void assignNonce(Long transactionId, long nonce) {
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }

    Web3TransactionEntity entity = load(transactionId);
    if (entity.getStatus() != Web3TxStatus.CREATED) {
      throw new Web3TransactionStateInvalidException(
          "nonce can only be assigned for CREATED status: id=" + transactionId);
    }

    if (entity.getNonce() != null && !entity.getNonce().equals(nonce)) {
      throw new Web3TransactionStateInvalidException(
          "nonce already assigned with different value: id="
              + transactionId
              + ", existing="
              + entity.getNonce()
              + ", requested="
              + nonce);
    }

    if (entity.getNonce() == null) {
      entity.setNonce(nonce);
    }
  }

  @Override
  @Transactional
  public void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash) {
    if (signedRawTx == null || signedRawTx.isBlank()) {
      throw new Web3InvalidInputException("signedRawTx is required");
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
      throw new Web3InvalidInputException("txHash is required");
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

  @Override
  @Transactional(readOnly = true)
  public java.util.Optional<LoadTransactionPort.TransactionSnapshot> loadById(Long transactionId) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }

    return repository
        .findById(transactionId)
        .map(
            entity ->
                new LoadTransactionPort.TransactionSnapshot(
                    entity.getId(), entity.getStatus(), entity.getTxHash(), entity.getFailureReason()));
  }

  private TransactionWorkItem toWorkItem(Web3TransactionEntity entity) {
    return new TransactionWorkItem(
        entity.getId(),
        entity.getFromAddress(),
        entity.getToAddress(),
        entity.getAmountWei(),
        entity.getNonce(),
        entity.getTxHash(),
        entity.getSignedRawTx(),
        entity.getFailureReason(),
        entity.getBroadcastedAt());
  }

  private Web3TransactionEntity load(Long transactionId) {
    return repository
        .findById(transactionId)
        .orElseThrow(() -> new Web3TransactionNotFoundException(transactionId));
  }

  @SuppressWarnings("unchecked")
  private List<Number> selectClaimableIds(Web3TxStatus status, int limit) {
    if (status == Web3TxStatus.CREATED) {
      return entityManager
          .createNativeQuery(CLAIM_CREATED_SQL)
          .setParameter("status", status.name())
          .setParameter("limit", limit)
          .getResultList();
    }

    return entityManager
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
  }
}
