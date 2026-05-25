package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter.nonce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.VerifyUnbroadcastableAttemptCommand;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceAttemptStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotAttemptEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotAttemptJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotEvidenceJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonceSlotPersistenceAdapterTest {

  private static final long CHAIN_ID = 84532L;
  private static final String SPONSOR = "0x" + "a".repeat(40);
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-24T12:00:00");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private NonceSlotJpaRepository slotRepository;
  @Mock private NonceSlotAttemptJpaRepository attemptRepository;
  @Mock private NonceSlotEvidenceJpaRepository evidenceRepository;
  @Mock private Web3TransactionJpaRepository transactionRepository;

  private NonceSlotPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    TransactionRewardTokenProperties properties = new TransactionRewardTokenProperties();
    properties.getWorker().setClaimTtlSeconds(120);
    properties.getWorker().setReceiptTimeoutSeconds(60);
    adapter =
        new NonceSlotPersistenceAdapter(
            slotRepository,
            attemptRepository,
            evidenceRepository,
            transactionRepository,
            properties,
            FIXED_CLOCK);
  }

  @Test
  void reserve_createsAttemptAndSlotAtomicallyForMatchingTransactionScope() {
    when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction(10L, 51L)));
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, SPONSOR, 51L)).thenReturn(Optional.empty());
    when(attemptRepository.findTopByChainIdAndFromAddressAndNonceOrderByAttemptNoDesc(
            CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.empty());
    when(attemptRepository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              NonceSlotAttemptEntity attempt = invocation.getArgument(0);
              attempt.setId(100L);
              return attempt;
            });
    when(slotRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        adapter.reserve(
            new ReserveSponsorNonceSlotCommand(
                CHAIN_ID, SPONSOR.toUpperCase(), 51L, 10L, "intent:sponsor:51:attempt:1", NOW));

    assertThat(result.attemptNo()).isEqualTo(1);
    assertThat(result.attemptId()).isEqualTo(100L);
    assertThat(result.transactionId()).isEqualTo(10L);
    ArgumentCaptor<NonceSlotEntity> slotCaptor = ArgumentCaptor.forClass(NonceSlotEntity.class);
    verify(slotRepository).saveAndFlush(slotCaptor.capture());
    assertThat(slotCaptor.getValue().getStatus()).isEqualTo(SponsorNonceSlotStatus.RESERVED);
    assertThat(slotCaptor.getValue().getActiveAttemptId()).isEqualTo(100L);
  }

  @Test
  void reserve_rejectsTransactionScopeMismatch() {
    Web3TransactionEntity transaction = transaction(10L, 51L);
    transaction.setChainId(1L);
    when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));

    assertThatThrownBy(
            () ->
                adapter.reserve(
                    new ReserveSponsorNonceSlotCommand(
                        CHAIN_ID, SPONSOR, 51L, 10L, "intent:sponsor:51:attempt:1", NOW)))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("chainId mismatch");
  }

  @Test
  void reserve_assignsNonceWhenCreatedTransactionHasNoNonceYet() {
    Web3TransactionEntity transaction = transaction(10L, 51L);
    transaction.setNonce(null);
    when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, SPONSOR, 51L)).thenReturn(Optional.empty());
    when(attemptRepository.findTopByChainIdAndFromAddressAndNonceOrderByAttemptNoDesc(
            CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.empty());
    when(attemptRepository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              NonceSlotAttemptEntity attempt = invocation.getArgument(0);
              attempt.setId(100L);
              return attempt;
            });
    when(slotRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    adapter.reserve(
        new ReserveSponsorNonceSlotCommand(
            CHAIN_ID, SPONSOR, 51L, 10L, "intent:sponsor:51:attempt:1", NOW));

    assertThat(transaction.getNonce()).isEqualTo(51L);
  }

  @Test
  void recordTransition_externalProofConsumedClosesActiveAttemptAsAbandoned() {
    NonceSlotEntity slot =
        NonceSlotEntity.builder()
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .status(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
            .attemptNo(1)
            .activeAttemptId(100L)
            .activeTxId(10L)
            .replacementPrepareAttemptCount(0)
            .broadcastRecoveryAttemptCount(0)
            .build();
    NonceSlotAttemptEntity attempt =
        NonceSlotAttemptEntity.builder()
            .id(100L)
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .attemptNo(1)
            .txId(10L)
            .status(SponsorNonceAttemptStatus.OPERATOR_REVIEW_REQUIRED)
            .idempotencyKey("intent:sponsor:51:attempt:1")
            .build();
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, SPONSOR, 51L)).thenReturn(Optional.of(slot));
    when(attemptRepository.findByIdAndChainIdAndFromAddressAndNonce(100L, CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(attempt));
    when(slotRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    adapter.recordTransition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .fromStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
            .toStatus(SponsorNonceSlotStatus.CONSUMED)
            .consumedExternalEvidenceId(200L)
            .terminalReason("EXTERNAL_ADMIN_CONSUMED_PROOF")
            .stateChangedAt(NOW)
            .build());

    assertThat(attempt.getStatus()).isEqualTo(SponsorNonceAttemptStatus.ABANDONED);
    assertThat(attempt.getTerminalReason()).isEqualTo("EXTERNAL_ADMIN_CONSUMED_PROOF");
  }

  @Test
  void recordTransition_backendReceiptConsumedKeepsAttemptTerminalReasonNull() {
    NonceSlotEntity slot = slotEntity(51L, SponsorNonceSlotStatus.BROADCASTED, 1, 100L, 10L);
    NonceSlotAttemptEntity attempt =
        NonceSlotAttemptEntity.builder()
            .id(100L)
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .attemptNo(1)
            .txId(10L)
            .status(SponsorNonceAttemptStatus.BROADCASTED)
            .idempotencyKey("intent:sponsor:51:attempt:1")
            .build();
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, SPONSOR, 51L)).thenReturn(Optional.of(slot));
    when(attemptRepository.findByIdAndChainIdAndFromAddressAndNonce(100L, CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(attempt));
    when(slotRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    adapter.recordTransition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .fromStatus(SponsorNonceSlotStatus.BROADCASTED)
            .toStatus(SponsorNonceSlotStatus.CONSUMED)
            .consumedTxId(10L)
            .consumedReason("RECEIPT_STATUS_1")
            .hasReceiptEvidence(true)
            .stateChangedAt(NOW)
            .build());

    assertThat(attempt.getStatus()).isEqualTo(SponsorNonceAttemptStatus.CONSUMED);
    assertThat(attempt.getTerminalReason()).isNull();
    assertThat(attempt.getReceiptObservedAt()).isEqualTo(NOW);
    assertThat(slot.getConsumedReason()).isEqualTo("RECEIPT_STATUS_1");
  }

  @Test
  void recordTransition_droppedUsesReleasedAttemptFallbackWhenCommandOmitsIt() {
    NonceSlotEntity slot = slotEntity(51L, SponsorNonceSlotStatus.RESERVED, 1, 100L, 10L);
    NonceSlotAttemptEntity attempt =
        NonceSlotAttemptEntity.builder()
            .id(100L)
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .attemptNo(1)
            .txId(10L)
            .status(SponsorNonceAttemptStatus.RESERVED)
            .idempotencyKey("intent:sponsor:51:attempt:1")
            .build();
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, SPONSOR, 51L)).thenReturn(Optional.of(slot));
    when(attemptRepository.findByIdAndChainIdAndFromAddressAndNonce(100L, CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(attempt));
    when(slotRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    adapter.recordTransition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .fromStatus(SponsorNonceSlotStatus.RESERVED)
            .toStatus(SponsorNonceSlotStatus.DROPPED)
            .releasedTxId(10L)
            .releaseReason("UNBROADCASTABLE_RESERVED_TIMEOUT")
            .stateChangedAt(NOW)
            .build());

    assertThat(slot.getReleasedAttemptId()).isEqualTo(100L);
    assertThat(attempt.getStatus()).isEqualTo(SponsorNonceAttemptStatus.DROPPED);
    assertThat(attempt.getTerminalReason()).isEqualTo("UNBROADCASTABLE_RESERVED_TIMEOUT");
  }

  @Test
  void recordTransition_rejectsStaleActiveTxOwnerBeforeMutatingSlot() {
    NonceSlotEntity slot = slotEntity(51L, SponsorNonceSlotStatus.RESERVED, 1, 100L, 10L);
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, SPONSOR, 51L)).thenReturn(Optional.of(slot));

    assertThatThrownBy(
            () ->
                adapter.recordTransition(
                    RecordSponsorNonceSlotTransitionCommand.builder()
                        .chainId(CHAIN_ID)
                        .fromAddress(SPONSOR)
                        .nonce(51L)
                        .fromStatus(SponsorNonceSlotStatus.RESERVED)
                        .toStatus(SponsorNonceSlotStatus.SIGNED)
                        .activeAttemptId(100L)
                        .activeTxId(11L)
                        .stateChangedAt(NOW)
                        .build()))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("activeTxId mismatch");

    assertThat(slot.getStatus()).isEqualTo(SponsorNonceSlotStatus.RESERVED);
  }

  @Test
  void verifyUnbroadcastable_requiresAttemptAndTransactionToHaveNoChainReachableEvidence() {
    NonceSlotAttemptEntity attempt =
        NonceSlotAttemptEntity.builder()
            .id(100L)
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .attemptNo(1)
            .txId(10L)
            .status(SponsorNonceAttemptStatus.RESERVED)
            .idempotencyKey("intent:sponsor:51:attempt:1")
            .build();
    when(attemptRepository.findByIdAndChainIdAndFromAddressAndNonce(100L, CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(attempt));
    when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction(10L, 51L)));

    boolean result =
        adapter.verifyUnbroadcastable(
            new VerifyUnbroadcastableAttemptCommand(CHAIN_ID, SPONSOR, 51L, 100L, NOW));

    assertThat(result).isTrue();
  }

  @Test
  void verifyUnbroadcastable_rejectsTransactionWithActiveProcessingOrRetryDeadline() {
    NonceSlotAttemptEntity attempt =
        NonceSlotAttemptEntity.builder()
            .id(100L)
            .chainId(CHAIN_ID)
            .fromAddress(SPONSOR)
            .nonce(51L)
            .attemptNo(1)
            .txId(10L)
            .status(SponsorNonceAttemptStatus.RESERVED)
            .idempotencyKey("intent:sponsor:51:attempt:1")
            .build();
    Web3TransactionEntity transaction = transaction(10L, 51L);
    transaction.setProcessingBy("issuer-1");
    transaction.setProcessingUntil(NOW.plusSeconds(30));
    when(attemptRepository.findByIdAndChainIdAndFromAddressAndNonce(100L, CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(attempt));
    when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));

    boolean result =
        adapter.verifyUnbroadcastable(
            new VerifyUnbroadcastableAttemptCommand(CHAIN_ID, SPONSOR, 51L, 100L, NOW));

    assertThat(result).isFalse();
  }

  @Test
  void loadSlotsForReview_returnsAllSlotsForScopeInRepositoryOrder() {
    NonceSlotEntity consumed = slotEntity(50L, SponsorNonceSlotStatus.CONSUMED, 1, 100L, 10L);
    consumed.setConsumedAttemptId(100L);
    consumed.setConsumedTxId(10L);
    NonceSlotEntity review =
        slotEntity(51L, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED, 2, 101L, 11L);
    when(slotRepository.findByScopeOrderByNonce(CHAIN_ID, SPONSOR))
        .thenReturn(List.of(consumed, review));

    var result = adapter.loadSlotsForReview(CHAIN_ID, SPONSOR.toUpperCase());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).nonce()).isEqualTo(50L);
    assertThat(result.get(0).status()).isEqualTo(SponsorNonceSlotStatus.CONSUMED);
    assertThat(result.get(1).nonce()).isEqualTo(51L);
    assertThat(result.get(1).status()).isEqualTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    verify(slotRepository).findByScopeOrderByNonce(CHAIN_ID, SPONSOR);
  }

  @Test
  void loadOpenOrBlockingSlots_mapsTimeoutAndReplacementEligibility() {
    NonceSlotEntity reserved = slotEntity(50L, SponsorNonceSlotStatus.RESERVED, 1, 100L, 10L);
    reserved.setUpdatedAt(NOW.minusSeconds(121));
    NonceSlotEntity broadcasted = slotEntity(51L, SponsorNonceSlotStatus.BROADCASTED, 1, 101L, 11L);
    broadcasted.setActiveTxHash("0x" + "b".repeat(64));
    broadcasted.setLastBroadcastedAt(NOW.minusSeconds(61));
    when(slotRepository.findByScopeAndStatusInOrderByNonce(anyLong(), any(String.class), any()))
        .thenReturn(List.of(reserved, broadcasted));

    var result = adapter.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR.toUpperCase());

    assertThat(result.get(0).timedOut()).isTrue();
    assertThat(result.get(1).timedOut()).isTrue();
    assertThat(result.get(1).replacementEligible()).isTrue();
  }

  private Web3TransactionEntity transaction(Long id, long nonce) {
    return Web3TransactionEntity.builder()
        .id(id)
        .idempotencyKey("intent:" + id)
        .referenceType(Web3ReferenceType.USER_TO_SERVER)
        .referenceId("resource-" + id)
        .fromAddress(SPONSOR)
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.ZERO)
        .nonce(nonce)
        .chainId(CHAIN_ID)
        .txType(Web3TxType.EIP7702)
        .status(Web3TxStatus.CREATED)
        .build();
  }

  private NonceSlotEntity slotEntity(
      long nonce, SponsorNonceSlotStatus status, int attemptNo, Long attemptId, Long txId) {
    return NonceSlotEntity.builder()
        .chainId(CHAIN_ID)
        .fromAddress(SPONSOR)
        .nonce(nonce)
        .status(status)
        .attemptNo(attemptNo)
        .activeAttemptId(attemptId)
        .activeTxId(txId)
        .replacementPrepareAttemptCount(0)
        .broadcastRecoveryAttemptCount(0)
        .updatedAt(NOW)
        .build();
  }
}
