package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ManageTransactionRecoveryPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionWorkPersistenceAdapterTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-08T00:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private EntityManager entityManager;
  @Mock private Web3TransactionJpaRepository repository;
  @Mock private NonceSlotJpaRepository nonceSlotRepository;

  private TransactionWorkPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new TransactionWorkPersistenceAdapter(
            entityManager, repository, nonceSlotRepository, FIXED_CLOCK);
  }

  @Test
  void claimByStatus_throws_whenStatusNull() {
    assertThatThrownBy(() -> adapter.claimByStatus(null, 10, "worker-1", Duration.ofMinutes(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void claimByStatus_throws_whenLimitNonPositive() {
    assertThatThrownBy(
            () -> adapter.claimByStatus(Web3TxStatus.CREATED, 0, "worker-1", Duration.ofMinutes(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("limit must be > 0");
  }

  @Test
  void claimByStatus_throws_whenWorkerBlank() {
    assertThatThrownBy(
            () -> adapter.claimByStatus(Web3TxStatus.CREATED, 10, " ", Duration.ofMinutes(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("workerId is required");
  }

  @Test
  void claimByStatus_returnsEmpty_whenNoClaimableIds() {
    Query claimQuery = mockQuery();
    when(entityManager.createNativeQuery(any(String.class))).thenReturn(claimQuery);
    when(claimQuery.getResultList()).thenReturn(List.of());

    List<LoadTransactionWorkPort.TransactionWorkItem> result =
        adapter.claimByStatus(Web3TxStatus.CREATED, 10, "worker-1", Duration.ofMinutes(1));

    assertThat(result).isEmpty();
    verify(entityManager, never()).createQuery(any(String.class));
  }

  @Test
  void claimByStatus_whenCreated_filtersToLegacyLevelRewardIssuerScope() {
    Query claimQuery = mockQuery();
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    when(entityManager.createNativeQuery(sqlCaptor.capture())).thenReturn(claimQuery);
    when(claimQuery.getResultList()).thenReturn(List.of());

    adapter.claimByStatus(Web3TxStatus.CREATED, 10, "worker-1", Duration.ofMinutes(1));

    assertThat(sqlCaptor.getValue()).contains("t.tx_type = 'EIP1559'");
    assertThat(sqlCaptor.getValue()).contains("t.reference_type = 'LEVEL_UP_REWARD'");
  }

  @Test
  void claimByStatus_updatesLockAndReturnsWorkItems_withDefaultTtlWhenNull() {
    Query claimQuery = mockQuery();
    Query updateQuery = mockQuery();
    Web3TransactionEntity first = baseEntity(1L, Web3TxStatus.CREATED);
    Web3TransactionEntity second = baseEntity(2L, Web3TxStatus.CREATED);

    when(entityManager.createNativeQuery(any(String.class))).thenReturn(claimQuery);
    when(claimQuery.getResultList()).thenReturn(List.of(1L, 2L));
    when(entityManager.createQuery(startsWith("update Web3TransactionEntity t set t.processingBy")))
        .thenReturn(updateQuery);
    when(updateQuery.executeUpdate()).thenReturn(2);
    when(repository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(first, second));

    List<LoadTransactionWorkPort.TransactionWorkItem> result =
        adapter.claimByStatus(Web3TxStatus.CREATED, 2, "worker-a", null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).transactionId()).isEqualTo(1L);
    assertThat(result.get(0).chainId()).isEqualTo(11155111L);
    assertThat(result.get(1).transactionId()).isEqualTo(2L);

    ArgumentCaptor<LocalDateTime> processingUntilCaptor =
        ArgumentCaptor.forClass(LocalDateTime.class);
    verify(updateQuery).setParameter(eq("processingUntil"), processingUntilCaptor.capture());
    assertThat(processingUntilCaptor.getValue()).isEqualTo(FIXED_NOW.plusMinutes(2));
  }

  @Test
  void claimByStatus_filtersOutEntitiesMissingFromRepository() {
    Query claimQuery = mockQuery();
    Query updateQuery = mockQuery();
    Web3TransactionEntity first = baseEntity(1L, Web3TxStatus.CREATED);

    when(entityManager.createNativeQuery(any(String.class))).thenReturn(claimQuery);
    when(claimQuery.getResultList()).thenReturn(List.of(1L, 2L));
    when(entityManager.createQuery(startsWith("update Web3TransactionEntity t set t.processingBy")))
        .thenReturn(updateQuery);
    when(updateQuery.executeUpdate()).thenReturn(2);
    when(repository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(first));

    List<LoadTransactionWorkPort.TransactionWorkItem> result =
        adapter.claimByStatus(Web3TxStatus.CREATED, 2, "worker-a", Duration.ofMinutes(3));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().transactionId()).isEqualTo(1L);
  }

  @Test
  void assignNonce_throws_whenNonceNegative() {
    assertThatThrownBy(() -> adapter.assignNonce(1L, -1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void assignNonce_updatesEntityWhenStateAllows() {
    Web3TransactionEntity entity = baseEntity(1L, Web3TxStatus.CREATED);
    when(repository.findById(1L)).thenReturn(Optional.of(entity));

    adapter.assignNonce(1L, 77L);

    assertThat(entity.getNonce()).isEqualTo(77L);
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void assignNonce_throwsWhenTransactionMissing() {
    when(repository.findById(123L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.assignNonce(123L, 1L))
        .isInstanceOf(Web3TransactionNotFoundException.class);
  }

  @Test
  void clearNonce_throws_whenTransactionIdInvalid() {
    assertThatThrownBy(() -> adapter.clearNonce(0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void clearNonce_throws_whenTransactionIdNull() {
    assertThatThrownBy(() -> adapter.clearNonce(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void clearNonce_clearsAssignedNonceAndStampsUpdatedAt() {
    Web3TransactionEntity entity = baseEntity(1L, Web3TxStatus.CREATED);
    entity.setNonce(77L);
    when(repository.findById(1L)).thenReturn(Optional.of(entity));

    adapter.clearNonce(1L);

    assertThat(entity.getNonce()).isNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void clearNonce_throwsWhenTransactionMissing() {
    when(repository.findById(123L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.clearNonce(123L))
        .isInstanceOf(Web3TransactionNotFoundException.class);
  }

  @Test
  void loadById_throws_whenTransactionIdInvalid() {
    assertThatThrownBy(() -> adapter.loadById(0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void loadById_returnsEmptyWhenNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    Optional<LoadTransactionPort.TransactionSnapshot> snapshot = adapter.loadById(99L);

    assertThat(snapshot).isEmpty();
  }

  @Test
  void loadById_mapsSnapshotWhenFound() {
    Web3TransactionEntity entity = baseEntity(1L, Web3TxStatus.PENDING);
    entity.setTxHash("0x" + "f".repeat(64));
    entity.setFailureReason("TIMEOUT");
    when(repository.findById(1L)).thenReturn(Optional.of(entity));

    Optional<LoadTransactionPort.TransactionSnapshot> snapshot = adapter.loadById(1L);

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().transactionId()).isEqualTo(1L);
    assertThat(snapshot.get().txHash()).isEqualTo("0x" + "f".repeat(64));
    assertThat(snapshot.get().failureReason()).isEqualTo("TIMEOUT");
  }

  @Test
  void loadLevelRewardsByReferenceIds_returnsEmpty_whenReferenceIdsNull() {
    List<LoadTransactionPort.TransactionSnapshot> snapshots =
        adapter.loadLevelRewardsByReferenceIds(null);

    assertThat(snapshots).isEmpty();
    verify(repository, never()).findLevelRewardsByReferenceIdIn(any());
  }

  @Test
  void loadLevelRewardsByReferenceIds_returnsEmpty_whenReferenceIdsEmpty() {
    List<LoadTransactionPort.TransactionSnapshot> snapshots =
        adapter.loadLevelRewardsByReferenceIds(List.of());

    assertThat(snapshots).isEmpty();
    verify(repository, never()).findLevelRewardsByReferenceIdIn(any());
  }

  @Test
  void markSigned_throws_whenSignedRawTxBlank() {
    assertThatThrownBy(() -> adapter.markSigned(1L, 1L, " ", "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signedRawTx is required");
  }

  @Test
  void markSigned_updatesEntity() {
    Web3TransactionEntity entity = baseEntity(10L, Web3TxStatus.CREATED);
    when(repository.findById(10L)).thenReturn(Optional.of(entity));

    adapter.markSigned(10L, 3L, "0xdeadbeef", "0x" + "a".repeat(64));

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.SIGNED);
    assertThat(entity.getNonce()).isEqualTo(3L);
    assertThat(entity.getSignedRawTx()).isEqualTo("0xdeadbeef");
    assertThat(entity.getTxHash()).isEqualTo("0x" + "a".repeat(64));
    assertThat(entity.getSignedAt()).isEqualTo(FIXED_NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void markSigned_keepsExistingHash_whenNewHashBlank() {
    Web3TransactionEntity entity = baseEntity(15L, Web3TxStatus.CREATED);
    entity.setTxHash("0x" + "d".repeat(64));
    when(repository.findById(15L)).thenReturn(Optional.of(entity));

    adapter.markSigned(15L, 5L, "0xdeadbeef", " ");

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.SIGNED);
    assertThat(entity.getTxHash()).isEqualTo("0x" + "d".repeat(64));
  }

  @Test
  void markPending_throws_whenTxHashBlank() {
    assertThatThrownBy(() -> adapter.markPending(1L, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void markPending_updatesEntity() {
    Web3TransactionEntity entity = baseEntity(11L, Web3TxStatus.SIGNED);
    when(repository.findById(11L)).thenReturn(Optional.of(entity));

    adapter.markPending(11L, "0x" + "b".repeat(64));

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.PENDING);
    assertThat(entity.getTxHash()).isEqualTo("0x" + "b".repeat(64));
    assertThat(entity.getBroadcastedAt()).isEqualTo(FIXED_NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void markPending_keepsExistingBroadcastedAt() {
    Web3TransactionEntity entity = baseEntity(16L, Web3TxStatus.SIGNED);
    LocalDateTime existingBroadcastedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
    entity.setBroadcastedAt(existingBroadcastedAt);
    when(repository.findById(16L)).thenReturn(Optional.of(entity));

    adapter.markPending(16L, "0x" + "b".repeat(64));

    assertThat(entity.getBroadcastedAt()).isEqualTo(existingBroadcastedAt);
  }

  @Test
  void updateStatus_updatesEntity() {
    Web3TransactionEntity entity = baseEntity(12L, Web3TxStatus.PENDING);
    when(repository.findById(12L)).thenReturn(Optional.of(entity));

    adapter.updateStatus(12L, Web3TxStatus.SUCCEEDED, "0x" + "c".repeat(64), null);

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.SUCCEEDED);
    assertThat(entity.getTxHash()).isEqualTo("0x" + "c".repeat(64));
    assertThat(entity.getConfirmedAt()).isEqualTo(FIXED_NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void updateStatus_keepsExistingHash_whenBlankHashGiven() {
    Web3TransactionEntity entity = baseEntity(17L, Web3TxStatus.PENDING);
    entity.setTxHash("0x" + "e".repeat(64));
    when(repository.findById(17L)).thenReturn(Optional.of(entity));

    adapter.updateStatus(17L, Web3TxStatus.UNCONFIRMED, " ", "TIMEOUT");

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.UNCONFIRMED);
    assertThat(entity.getTxHash()).isEqualTo("0x" + "e".repeat(64));
    assertThat(entity.getFailureReason()).isEqualTo("TIMEOUT");
  }

  @Test
  void markUnconfirmedForSponsorNonceReview_setsBroadcastedAtForSignedReview() {
    Web3TransactionEntity entity = baseEntity(18L, Web3TxStatus.SIGNED);
    entity.setTxHash("0x" + "f".repeat(64));
    entity.setSignedAt(FIXED_NOW.minusMinutes(1));
    entity.setProcessingBy("signed-recovery-worker");
    entity.setProcessingUntil(FIXED_NOW.plusMinutes(1));
    when(repository.findById(18L)).thenReturn(Optional.of(entity));

    adapter.markUnconfirmedForSponsorNonceReview(18L, "SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED");

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.UNCONFIRMED);
    assertThat(entity.getFailureReason()).isEqualTo("SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED");
    assertThat(entity.getBroadcastedAt()).isEqualTo(FIXED_NOW);
    assertThat(entity.getProcessingBy()).isNull();
    assertThat(entity.getProcessingUntil()).isNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void claimForProcessing_updatesLockWhenStatusAndLockAreClaimable() {
    Query updateQuery = mockQuery();
    LocalDateTime processingUntil = FIXED_NOW.plusSeconds(30);
    when(entityManager.createQuery(startsWith("update Web3TransactionEntity t set t.processingBy")))
        .thenReturn(updateQuery);
    when(updateQuery.executeUpdate()).thenReturn(1);

    boolean result =
        adapter.claimForProcessing(
            10L, Web3TxStatus.SIGNED, "execution-broadcast-10", processingUntil);

    assertThat(result).isTrue();
    verify(updateQuery).setParameter("workerId", "execution-broadcast-10");
    verify(updateQuery).setParameter("processingUntil", processingUntil);
    verify(updateQuery).setParameter("updatedAt", FIXED_NOW);
    verify(updateQuery).setParameter("transactionId", 10L);
    verify(updateQuery).setParameter("status", Web3TxStatus.SIGNED);
    verify(updateQuery).setParameter("now", FIXED_NOW);
  }

  @Test
  void claimForProcessing_returnsFalseWhenNoRowMatches() {
    Query updateQuery = mockQuery();
    when(entityManager.createQuery(startsWith("update Web3TransactionEntity t set t.processingBy")))
        .thenReturn(updateQuery);
    when(updateQuery.executeUpdate()).thenReturn(0);

    boolean result =
        adapter.claimForProcessing(
            10L, Web3TxStatus.SIGNED, "execution-broadcast-10", FIXED_NOW.plusSeconds(30));

    assertThat(result).isFalse();
  }

  @Test
  void loadLevelRewardsByReferenceIds_mapsSnapshots() {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .id(1L)
            .idempotencyKey("idem-1")
            .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
            .referenceId("ref-1")
            .fromUserId(7L)
            .toUserId(22L)
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .chainId(11155111L)
            .txType(Web3TxType.EIP7702)
            .status(Web3TxStatus.SIGNED)
            .txHash("0x" + "c".repeat(64))
            .build();
    when(repository.findLevelRewardsByReferenceIdIn(List.of("ref-1"))).thenReturn(List.of(entity));

    List<LoadTransactionPort.TransactionSnapshot> snapshots =
        adapter.loadLevelRewardsByReferenceIds(List.of("ref-1"));

    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.getFirst().transactionId()).isEqualTo(1L);
    assertThat(snapshots.getFirst().status()).isEqualTo(Web3TxStatus.SIGNED);
    assertThat(snapshots.getFirst().txHash()).isEqualTo("0x" + "c".repeat(64));
  }

  @Test
  void loadByIdForUpdate_mapsRecoverySnapshotWhenFound() {
    Web3TransactionEntity entity = baseEntity(30L, Web3TxStatus.CREATED);
    entity.setReferenceType(Web3ReferenceType.LEVEL_UP_REWARD);
    entity.setReferenceId("reward-30");
    entity.setFailureReason(Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code());
    when(entityManager.find(
            Web3TransactionEntity.class, 30L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(entity);

    Optional<ManageTransactionRecoveryPort.RecoverySnapshot> snapshot =
        adapter.loadByIdForUpdate(30L);

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().transactionId()).isEqualTo(30L);
    assertThat(snapshot.get().txType()).isEqualTo(Web3TxType.EIP1559);
    assertThat(snapshot.get().failureReason())
        .isEqualTo(Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code());
  }

  @Test
  void clearFailureForRequeue_clearsFailureAndLockForEligibleCreatedReward() {
    Web3TransactionEntity entity = baseEntity(31L, Web3TxStatus.CREATED);
    entity.setReferenceType(Web3ReferenceType.LEVEL_UP_REWARD);
    entity.setReferenceId("reward-31");
    entity.setFailureReason(Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code());
    entity.setProcessingBy("issuer-worker");
    entity.setProcessingUntil(FIXED_NOW.plusMinutes(2));
    when(entityManager.find(
            Web3TransactionEntity.class, 31L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(entity);

    ManageTransactionRecoveryPort.RequeueMutation result = adapter.clearFailureForRequeue(31L);

    assertThat(result.transactionId()).isEqualTo(31L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.CREATED);
    assertThat(result.originalFailureReason())
        .isEqualTo(Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code());
    assertThat(entity.getFailureReason()).isNull();
    assertThat(entity.getProcessingBy()).isNull();
    assertThat(entity.getProcessingUntil()).isNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void clearFailureForRequeue_reactivatesDroppedSlotWhenReleasedByTransaction() {
    Web3TransactionEntity entity = baseEntity(32L, Web3TxStatus.CREATED);
    entity.setReferenceType(Web3ReferenceType.LEVEL_UP_REWARD);
    entity.setReferenceId("reward-32");
    entity.setFailureReason(Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code());
    entity.setNonce(51L);
    entity.setProcessingBy("issuer-worker");
    entity.setProcessingUntil(FIXED_NOW.plusMinutes(2));
    when(entityManager.find(
            Web3TransactionEntity.class, 32L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(entity);
    NonceSlotEntity slot =
        droppedSlot(
            entity.getId(), entity.getNonce(), Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL);
    NonceSlotAttemptEntity attempt =
        droppedAttempt(
            slot.getReleasedAttemptId(),
            entity.getId(),
            entity.getNonce(),
            Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL);
    when(nonceSlotRepository.findByScopeForUpdate(
            entity.getChainId(), entity.getFromAddress(), entity.getNonce()))
        .thenReturn(Optional.of(slot));
    when(entityManager.find(
            NonceSlotAttemptEntity.class,
            slot.getReleasedAttemptId(),
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(attempt);

    ManageTransactionRecoveryPort.RequeueMutation result = adapter.clearFailureForRequeue(32L);

    assertThat(result.transactionId()).isEqualTo(32L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.CREATED);
    assertThat(result.originalFailureReason())
        .isEqualTo(Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code());
    assertThat(entity.getNonce()).isEqualTo(51L);
    assertThat(entity.getFailureReason()).isNull();
    assertThat(entity.getProcessingBy()).isNull();
    assertThat(entity.getProcessingUntil()).isNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_NOW);
    assertThat(slot.getStatus()).isEqualTo(SponsorNonceSlotStatus.RESERVED);
    assertThat(slot.getActiveAttemptId()).isEqualTo(attempt.getId());
    assertThat(slot.getActiveTxId()).isEqualTo(entity.getId());
    assertThat(slot.getReleasedAttemptId()).isNull();
    assertThat(slot.getReleasedTxId()).isNull();
    assertThat(slot.getReleaseReason()).isNull();
    assertThat(attempt.getStatus()).isEqualTo(SponsorNonceAttemptStatus.RESERVED);
    assertThat(attempt.getTerminalReason()).isNull();
  }

  @Test
  void clearFailureForRequeue_rejectsNonceWhenDroppedSlotBelongsToOtherTransaction() {
    Web3TransactionEntity entity = baseEntity(33L, Web3TxStatus.CREATED);
    entity.setReferenceType(Web3ReferenceType.LEVEL_UP_REWARD);
    entity.setReferenceId("reward-33");
    entity.setFailureReason(Web3TxFailureReason.PREVALIDATE_REVERT.code());
    entity.setNonce(52L);
    entity.setProcessingBy("issuer-worker");
    entity.setProcessingUntil(FIXED_NOW.plusMinutes(2));
    when(entityManager.find(
            Web3TransactionEntity.class, 33L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(entity);
    when(nonceSlotRepository.findByScopeForUpdate(
            entity.getChainId(), entity.getFromAddress(), entity.getNonce()))
        .thenReturn(
            Optional.of(
                droppedSlot(99L, entity.getNonce(), Web3TxFailureReason.PREVALIDATE_REVERT)));

    assertThatThrownBy(() -> adapter.clearFailureForRequeue(33L))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("pre-sign transaction");

    assertThat(entity.getNonce()).isEqualTo(52L);
    assertThat(entity.getFailureReason()).isEqualTo(Web3TxFailureReason.PREVALIDATE_REVERT.code());
    assertThat(entity.getProcessingBy()).isEqualTo("issuer-worker");
    assertThat(entity.getProcessingUntil()).isEqualTo(FIXED_NOW.plusMinutes(2));
  }

  @Test
  void clearFailureForRequeue_rejectsNonceWhenSlotIsStillReserved() {
    Web3TransactionEntity entity = baseEntity(34L, Web3TxStatus.CREATED);
    entity.setReferenceType(Web3ReferenceType.LEVEL_UP_REWARD);
    entity.setReferenceId("reward-34");
    entity.setFailureReason(Web3TxFailureReason.SIGNATURE_INVALID.code());
    entity.setNonce(53L);
    when(entityManager.find(
            Web3TransactionEntity.class, 34L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(entity);
    when(nonceSlotRepository.findByScopeForUpdate(
            entity.getChainId(), entity.getFromAddress(), entity.getNonce()))
        .thenReturn(Optional.of(reservedSlot(entity.getId(), entity.getNonce())));

    assertThatThrownBy(() -> adapter.clearFailureForRequeue(34L))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("pre-sign transaction");

    assertThat(entity.getNonce()).isEqualTo(53L);
    assertThat(entity.getFailureReason()).isEqualTo(Web3TxFailureReason.SIGNATURE_INVALID.code());
  }

  private Query mockQuery() {
    Query query = Mockito.mock(Query.class);
    when(query.setParameter(any(String.class), any())).thenReturn(query);
    return query;
  }

  private Web3TransactionEntity baseEntity(Long id, Web3TxStatus status) {
    LocalDateTime now = LocalDateTime.of(2026, 3, 1, 0, 0);
    return Web3TransactionEntity.builder()
        .id(id)
        .idempotencyKey("idem-" + id)
        .referenceType(Web3ReferenceType.USER_TO_USER)
        .referenceId("ref-" + id)
        .fromUserId(1L)
        .toUserId(2L)
        .fromAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.TEN)
        .chainId(11155111L)
        .txType(Web3TxType.EIP1559)
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private NonceSlotEntity droppedSlot(
      Long releasedTxId, Long nonce, Web3TxFailureReason releaseReason) {
    return NonceSlotEntity.builder()
        .chainId(11155111L)
        .fromAddress("0x" + "a".repeat(40))
        .nonce(nonce)
        .status(SponsorNonceSlotStatus.DROPPED)
        .attemptNo(0)
        .releasedAttemptId(1001L)
        .releasedTxId(releasedTxId)
        .releasedAt(FIXED_NOW.minusMinutes(1))
        .releaseReason(releaseReason.code())
        .replacementPrepareAttemptCount(0)
        .broadcastRecoveryAttemptCount(0)
        .version(0L)
        .createdAt(FIXED_NOW.minusMinutes(2))
        .updatedAt(FIXED_NOW.minusMinutes(1))
        .build();
  }

  private NonceSlotAttemptEntity droppedAttempt(
      Long attemptId, Long transactionId, Long nonce, Web3TxFailureReason terminalReason) {
    return NonceSlotAttemptEntity.builder()
        .id(attemptId)
        .chainId(11155111L)
        .fromAddress("0x" + "a".repeat(40))
        .nonce(nonce)
        .attemptNo(0)
        .txId(transactionId)
        .status(SponsorNonceAttemptStatus.DROPPED)
        .idempotencyKey("attempt-" + transactionId)
        .terminalReason(terminalReason.code())
        .createdAt(FIXED_NOW.minusMinutes(2))
        .updatedAt(FIXED_NOW.minusMinutes(1))
        .build();
  }

  private NonceSlotEntity reservedSlot(Long activeTxId, Long nonce) {
    return NonceSlotEntity.builder()
        .chainId(11155111L)
        .fromAddress("0x" + "a".repeat(40))
        .nonce(nonce)
        .status(SponsorNonceSlotStatus.RESERVED)
        .attemptNo(0)
        .activeAttemptId(1001L)
        .activeTxId(activeTxId)
        .replacementPrepareAttemptCount(0)
        .broadcastRecoveryAttemptCount(0)
        .version(0L)
        .createdAt(FIXED_NOW.minusMinutes(2))
        .updatedAt(FIXED_NOW.minusMinutes(1))
        .build();
  }
}
