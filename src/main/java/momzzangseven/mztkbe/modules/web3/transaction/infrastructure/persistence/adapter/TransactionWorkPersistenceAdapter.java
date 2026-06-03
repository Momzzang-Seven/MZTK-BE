package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ManageTransactionRecoveryPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceAttemptStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotAttemptEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
/**
 * Persistence adapter for transaction worker lifecycle operations.
 *
 * <p>This adapter handles claim-lock semantics, state transitions, and snapshot reads used by
 * issuer/receipt/recovery workers.
 */
public class TransactionWorkPersistenceAdapter
    implements LoadTransactionWorkPort,
        LoadTransactionPort,
        UpdateTransactionPort,
        ManageTransactionRecoveryPort {

  private static final String NON_RETRYABLE_FAILURE_REASON_SQL =
      Arrays.stream(Web3TxFailureReason.values())
          .filter(reason -> !reason.isRetryable())
          .map(Web3TxFailureReason::code)
          .map(code -> "'" + code + "'")
          .collect(Collectors.joining(","));

  private static final String CLAIM_BY_STATUS_SQL_TEMPLATE =
      """
      SELECT t.id
      FROM web3_transactions t
      WHERE t.status = :status
        AND (t.processing_until IS NULL OR t.processing_until < :now)
        AND (
            t.failure_reason IS NULL
            OR t.failure_reason NOT IN (%s)
        )
        %s
      ORDER BY t.id
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """;

  private final EntityManager entityManager;
  private final Web3TransactionJpaRepository repository;
  private final NonceSlotJpaRepository nonceSlotRepository;
  private final Clock appClock;

  /** Claims transactions by status with lock ttl and worker ownership in one transaction. */
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

    LocalDateTime now = LocalDateTime.now(appClock);
    LocalDateTime processingUntil = now.plus(claimTtl == null ? Duration.ofMinutes(2) : claimTtl);

    List<Number> ids = selectClaimableIds(status, limit, now);

    if (ids.isEmpty()) {
      return List.of();
    }

    List<Long> longIds = ids.stream().map(Number::longValue).toList();

    entityManager
        .createQuery(
            "update Web3TransactionEntity t set t.processingBy = :workerId,"
                + " t.processingUntil = :processingUntil, t.updatedAt = :updatedAt where t.id in :ids")
        .setParameter("workerId", workerId)
        .setParameter("processingUntil", processingUntil)
        .setParameter("updatedAt", now)
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
    Web3Transaction transaction = toDomain(entity);
    transaction.assignNonce(nonce);
    apply(entity, transaction);
    entity.setUpdatedAt(LocalDateTime.now(appClock));
  }

  @Override
  @Transactional
  public void clearNonce(Long transactionId) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }

    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    transaction.clearNonce();
    apply(entity, transaction);
    entity.setUpdatedAt(LocalDateTime.now(appClock));
  }

  @Override
  @Transactional
  public void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash) {
    if (signedRawTx == null || signedRawTx.isBlank()) {
      throw new Web3InvalidInputException("signedRawTx is required");
    }

    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    LocalDateTime now = LocalDateTime.now(appClock);
    transaction.markSigned(nonce, signedRawTx, txHash, now);
    apply(entity, transaction);
    entity.setUpdatedAt(now);
  }

  @Override
  @Transactional
  public void markPending(Long transactionId, String txHash) {
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException("txHash is required");
    }

    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    LocalDateTime now = LocalDateTime.now(appClock);
    transaction.markPending(txHash, now);
    apply(entity, transaction);
    entity.setUpdatedAt(now);
  }

  @Override
  @Transactional
  public void updateStatus(
      Long transactionId, Web3TxStatus status, String txHash, String failureReason) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    LocalDateTime now = LocalDateTime.now(appClock);
    transaction.updateStatus(status, txHash, failureReason, now);
    apply(entity, transaction);
    entity.setUpdatedAt(now);
  }

  @Override
  @Transactional
  public void markUnconfirmedForSponsorNonceReview(Long transactionId, String failureReason) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    LocalDateTime now = LocalDateTime.now(appClock);
    transaction.markUnconfirmedForSponsorNonceReview(failureReason, now);
    apply(entity, transaction);
    entity.setUpdatedAt(now);
  }

  @Override
  @Transactional
  public void scheduleRetry(
      Long transactionId, String failureReason, LocalDateTime processingUntil) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    transaction.scheduleRetry(failureReason, processingUntil);
    apply(entity, transaction);
    entity.setUpdatedAt(LocalDateTime.now(appClock));
  }

  @Override
  @Transactional
  public boolean claimForProcessing(
      Long transactionId, Web3TxStatus status, String workerId, LocalDateTime processingUntil) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null) {
      throw new Web3InvalidInputException("status is required");
    }
    if (workerId == null || workerId.isBlank()) {
      throw new Web3InvalidInputException("workerId is required");
    }
    if (processingUntil == null) {
      throw new Web3InvalidInputException("processingUntil is required");
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    int updated =
        entityManager
            .createQuery(
                "update Web3TransactionEntity t set t.processingBy = :workerId,"
                    + " t.processingUntil = :processingUntil, t.updatedAt = :updatedAt"
                    + " where t.id = :transactionId and t.status = :status"
                    + " and (t.processingUntil is null or t.processingUntil < :now)")
            .setParameter("workerId", workerId)
            .setParameter("processingUntil", processingUntil)
            .setParameter("updatedAt", now)
            .setParameter("transactionId", transactionId)
            .setParameter("status", status)
            .setParameter("now", now)
            .executeUpdate();
    return updated == 1;
  }

  @Override
  @Transactional
  public void clearProcessingLock(Long transactionId) {
    Web3TransactionEntity entity = load(transactionId);
    Web3Transaction transaction = toDomain(entity);
    transaction.clearProcessingLock();
    apply(entity, transaction);
    entity.setUpdatedAt(LocalDateTime.now(appClock));
  }

  @Override
  @Transactional
  public Optional<RecoverySnapshot> loadByIdForUpdate(Long transactionId) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    return Optional.ofNullable(loadForUpdate(transactionId)).map(this::toRecoverySnapshot);
  }

  @Override
  @Transactional(
      noRollbackFor = {Web3InvalidInputException.class, Web3TransactionStateInvalidException.class})
  public RequeueMutation clearFailureForRequeue(Long transactionId) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }

    Web3TransactionEntity entity = loadForUpdate(transactionId);
    if (entity == null) {
      throw new Web3TransactionNotFoundException(transactionId);
    }

    Web3Transaction transaction = toDomain(entity);
    DroppedNonceReservation droppedNonceReservation =
        loadDroppedNonceReservationForRequeue(entity).orElse(null);
    Web3Transaction.RequeueDecision decision =
        transaction.clearFailureForRequeue(droppedNonceReservation != null);
    LocalDateTime now = LocalDateTime.now(appClock);
    if (droppedNonceReservation != null) {
      reactivateDroppedNonceReservation(droppedNonceReservation, now);
    }
    apply(entity, transaction);
    entity.setUpdatedAt(now);
    return new RequeueMutation(
        entity.getId(),
        transaction.getStatus(),
        decision.previousStatus(),
        decision.originalFailureReason());
  }

  @Override
  @Transactional(readOnly = true)
  public Page<RecoverySnapshot> loadPage(RecoveryQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }

    Specification<Web3TransactionEntity> specification =
        Specification.where(hasStatus(query.status()))
            .and(hasFailureReason(query.failureReason()))
            .and(hasReferenceType(query.referenceType()))
            .and(hasReferenceId(query.referenceId()))
            .and(hasTxType(query.txType()));

    return repository
        .findAll(
            specification,
            PageRequest.of(query.page(), query.size(), Sort.by(Sort.Order.asc("id"))))
        .map(this::toRecoverySnapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LoadTransactionPort.TransactionSnapshot> loadById(Long transactionId) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }

    return repository
        .findById(transactionId)
        .map(
            entity ->
                new LoadTransactionPort.TransactionSnapshot(
                    entity.getId(),
                    entity.getIdempotencyKey(),
                    entity.getReferenceType(),
                    entity.getReferenceId(),
                    entity.getFromUserId(),
                    entity.getToUserId(),
                    entity.getChainId(),
                    entity.getFromAddress(),
                    entity.getNonce(),
                    entity.getStatus(),
                    entity.getTxHash(),
                    entity.getFailureReason()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LoadTransactionPort.TransactionSnapshot> loadLevelRewardsByReferenceIds(
      Collection<String> referenceIds) {
    if (referenceIds == null || referenceIds.isEmpty()) {
      return List.of();
    }
    return repository.findLevelRewardsByReferenceIdIn(referenceIds).stream()
        .map(
            entity ->
                new LoadTransactionPort.TransactionSnapshot(
                    entity.getId(),
                    entity.getIdempotencyKey(),
                    entity.getReferenceType(),
                    entity.getReferenceId(),
                    entity.getFromUserId(),
                    entity.getToUserId(),
                    entity.getChainId(),
                    entity.getFromAddress(),
                    entity.getNonce(),
                    entity.getStatus(),
                    entity.getTxHash(),
                    entity.getFailureReason()))
        .toList();
  }

  private TransactionWorkItem toWorkItem(Web3TransactionEntity entity) {
    return new TransactionWorkItem(
        entity.getId(),
        entity.getIdempotencyKey(),
        entity.getReferenceType(),
        entity.getReferenceId(),
        entity.getFromUserId(),
        entity.getToUserId(),
        entity.getChainId(),
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

  private Web3TransactionEntity loadForUpdate(Long transactionId) {
    return entityManager.find(
        Web3TransactionEntity.class, transactionId, LockModeType.PESSIMISTIC_WRITE);
  }

  private Web3Transaction toDomain(Web3TransactionEntity entity) {
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
        entity.getTxType(),
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

  private RecoverySnapshot toRecoverySnapshot(Web3TransactionEntity entity) {
    return new RecoverySnapshot(
        entity.getId(),
        entity.getIdempotencyKey(),
        entity.getReferenceType(),
        entity.getReferenceId(),
        entity.getTxType(),
        entity.getFromUserId(),
        entity.getToUserId(),
        entity.getFromAddress(),
        entity.getToAddress(),
        entity.getStatus(),
        entity.getTxHash(),
        entity.getFailureReason(),
        entity.getNonce(),
        entity.getSignedRawTx(),
        entity.getSignedAt(),
        entity.getBroadcastedAt(),
        entity.getConfirmedAt(),
        entity.getProcessingBy(),
        entity.getProcessingUntil(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private Optional<DroppedNonceReservation> loadDroppedNonceReservationForRequeue(
      Web3TransactionEntity entity) {
    if (entity.getNonce() == null || entity.getChainId() == null) {
      return Optional.empty();
    }
    if (entity.getFromAddress() == null || entity.getFromAddress().isBlank()) {
      return Optional.empty();
    }

    String fromAddress = EvmAddress.of(entity.getFromAddress()).value();
    return nonceSlotRepository
        .findByScopeForUpdate(entity.getChainId(), fromAddress, entity.getNonce())
        .filter(slot -> isDroppedReleasedByTransaction(slot, entity))
        .flatMap(slot -> loadReleasedAttemptForUpdate(slot, entity));
  }

  private boolean isDroppedReleasedByTransaction(
      NonceSlotEntity slot, Web3TransactionEntity entity) {
    return slot.getStatus() == SponsorNonceSlotStatus.DROPPED
        && entity.getId() != null
        && entity.getId().equals(slot.getReleasedTxId())
        && slot.getReleasedAttemptId() != null
        && entity.getFailureReason() != null
        && entity.getFailureReason().equals(slot.getReleaseReason())
        && slot.getActiveTxId() == null
        && slot.getActiveAttemptId() == null
        && slot.getActiveTxHash() == null;
  }

  private Optional<DroppedNonceReservation> loadReleasedAttemptForUpdate(
      NonceSlotEntity slot, Web3TransactionEntity entity) {
    NonceSlotAttemptEntity attempt =
        entityManager.find(
            NonceSlotAttemptEntity.class,
            slot.getReleasedAttemptId(),
            LockModeType.PESSIMISTIC_WRITE);
    if (attempt == null || !isDroppedAttemptOwnedByTransaction(attempt, slot, entity)) {
      return Optional.empty();
    }
    return Optional.of(new DroppedNonceReservation(slot, attempt));
  }

  private boolean isDroppedAttemptOwnedByTransaction(
      NonceSlotAttemptEntity attempt, NonceSlotEntity slot, Web3TransactionEntity entity) {
    return attempt.getStatus() == SponsorNonceAttemptStatus.DROPPED
        && entity.getId().equals(attempt.getTxId())
        && slot.getChainId().equals(attempt.getChainId())
        && slot.getFromAddress().equals(attempt.getFromAddress())
        && slot.getNonce().equals(attempt.getNonce())
        && attempt.getTxHash() == null
        && attempt.getSignedAt() == null
        && attempt.getBroadcastStartedAt() == null
        && attempt.getBroadcastedAt() == null;
  }

  private void reactivateDroppedNonceReservation(
      DroppedNonceReservation reservation, LocalDateTime now) {
    NonceSlotEntity slot = reservation.slot();
    NonceSlotAttemptEntity attempt = reservation.attempt();

    slot.setStatus(SponsorNonceSlotStatus.RESERVED);
    slot.setActiveAttemptId(attempt.getId());
    slot.setActiveTxId(attempt.getTxId());
    slot.setActiveTxHash(null);
    slot.setReleasedAttemptId(null);
    slot.setReleasedTxId(null);
    slot.setReleasedAt(null);
    slot.setReleaseReason(null);
    slot.setStuckReason(null);
    slot.setReplacementClaimOwner(null);
    slot.setReplacementClaimExpiresAt(null);
    slot.setReplacementPrepareAttemptCount(0);
    slot.setBroadcastStartedAt(null);
    slot.setLastBroadcastedAt(null);
    slot.setBroadcastRecoveryClaimOwner(null);
    slot.setBroadcastRecoveryClaimToken(null);
    slot.setBroadcastRecoveryClaimExpiresAt(null);
    slot.setBroadcastRecoveryAttemptCount(0);
    slot.setUpdatedAt(now);

    attempt.setStatus(SponsorNonceAttemptStatus.RESERVED);
    attempt.setTerminalReason(null);
    attempt.setUpdatedAt(now);
  }

  private record DroppedNonceReservation(NonceSlotEntity slot, NonceSlotAttemptEntity attempt) {}

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
  private List<Number> selectClaimableIds(Web3TxStatus status, int limit, LocalDateTime now) {
    return entityManager
        .createNativeQuery(claimByStatusSql(status))
        .setParameter("status", status.name())
        .setParameter("now", now)
        .setParameter("limit", limit)
        .getResultList();
  }

  private String claimByStatusSql(Web3TxStatus status) {
    return CLAIM_BY_STATUS_SQL_TEMPLATE.formatted(
        NON_RETRYABLE_FAILURE_REASON_SQL, nonceSlotPredicate(status));
  }

  private String nonceSlotPredicate(Web3TxStatus status) {
    return switch (status) {
      case CREATED ->
          """
          AND t.tx_type = 'EIP1559'
          AND t.reference_type = 'LEVEL_UP_REWARD'
          """;
      case SIGNED ->
          """
          AND (
              NOT EXISTS (
                  SELECT 1
                  FROM web3_nonce_slots ns_any
                  WHERE ns_any.active_tx_id = t.id
              )
              OR EXISTS (
                  SELECT 1
                  FROM web3_nonce_slots ns
                  WHERE ns.active_tx_id = t.id
                    AND ns.status IN ('SIGNED', 'BROADCASTING')
              )
          )
          """;
      case PENDING ->
          """
          AND (
              NOT EXISTS (
                  SELECT 1
                  FROM web3_nonce_slots ns_any
                  WHERE ns_any.active_tx_id = t.id
              )
              OR EXISTS (
                  SELECT 1
                  FROM web3_nonce_slots ns
                  WHERE ns.active_tx_id = t.id
                    AND ns.status IN ('BROADCASTED', 'BROADCASTING')
              )
          )
          """;
      default -> "";
    };
  }

  private Specification<Web3TransactionEntity> hasStatus(Web3TxStatus status) {
    return (root, query, cb) ->
        status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
  }

  private Specification<Web3TransactionEntity> hasFailureReason(String failureReason) {
    return (root, query, cb) ->
        failureReason == null
            ? cb.conjunction()
            : cb.equal(root.get("failureReason"), failureReason);
  }

  private Specification<Web3TransactionEntity> hasReferenceType(Web3ReferenceType referenceType) {
    return (root, query, cb) ->
        referenceType == null
            ? cb.conjunction()
            : cb.equal(root.get("referenceType"), referenceType);
  }

  private Specification<Web3TransactionEntity> hasReferenceId(String referenceId) {
    return (root, query, cb) ->
        referenceId == null ? cb.conjunction() : cb.equal(root.get("referenceId"), referenceId);
  }

  private Specification<Web3TransactionEntity> hasTxType(Web3TxType txType) {
    return (root, query, cb) ->
        txType == null ? cb.conjunction() : cb.equal(root.get("txType"), txType);
  }
}
