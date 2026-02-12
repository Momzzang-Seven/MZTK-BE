package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.mapper.Web3TransactionMapper;
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
  private final Web3TransactionMapper mapper;

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
    Web3Transaction transaction = mapper.toDomain(entity);
    transaction.assignNonce(nonce);
    apply(entity, transaction);
  }

  @Override
  @Transactional
  public void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash) {
    if (signedRawTx == null || signedRawTx.isBlank()) {
      throw new Web3InvalidInputException("signedRawTx is required");
    }

    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = mapper.toDomain(entity);
    transaction.markSigned(nonce, signedRawTx, txHash, LocalDateTime.now());
    apply(entity, transaction);
  }

  @Override
  @Transactional
  public void markPending(Long transactionId, String txHash) {
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException("txHash is required");
    }

    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = mapper.toDomain(entity);
    transaction.markPending(txHash, LocalDateTime.now());
    apply(entity, transaction);
  }

  @Override
  @Transactional
  public void updateStatus(
      Long transactionId, Web3TxStatus status, String txHash, String failureReason) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = mapper.toDomain(entity);
    transaction.updateStatus(status, txHash, failureReason, LocalDateTime.now());
    apply(entity, transaction);
  }

  @Override
  @Transactional
  public void scheduleRetry(
      Long transactionId, String failureReason, LocalDateTime processingUntil) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = mapper.toDomain(entity);
    transaction.scheduleRetry(failureReason, processingUntil);
    apply(entity, transaction);
  }

  @Override
  @Transactional
  public void clearProcessingLock(Long transactionId) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = mapper.toDomain(entity);
    transaction.clearProcessingLock();
    apply(entity, transaction);
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
                    entity.getId(),
                    entity.getStatus(),
                    entity.getTxHash(),
                    entity.getFailureReason()));
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

  private void apply(Web3TransactionEntity entity, Web3Transaction domain) {
    entity.setNonce(domain.getNonce());
    entity.setStatus(domain.getStatus());
    entity.setTxHash(domain.getTxHash());
    entity.setSignedAt(domain.getSignedAt());
    entity.setBroadcastedAt(domain.getBroadcastedAt());
    entity.setConfirmedAt(domain.getConfirmedAt());
    entity.setSignedRawTx(domain.getSignedRawTx());
    entity.setFailureReason(domain.getFailureReason());
    entity.setProcessingBy(domain.getProcessingBy());
    entity.setProcessingUntil(domain.getProcessingUntil());
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
