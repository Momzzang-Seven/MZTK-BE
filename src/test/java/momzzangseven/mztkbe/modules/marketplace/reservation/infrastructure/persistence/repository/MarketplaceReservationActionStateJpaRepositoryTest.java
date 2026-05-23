package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationActionStateEntity;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.entity.Web3ExecutionIntentEntity;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository.Web3ExecutionIntentJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MarketplaceReservationActionStateJpaRepositoryTest {

  @Autowired private MarketplaceReservationActionStateJpaRepository actionStateRepository;
  @Autowired private Web3ExecutionIntentJpaRepository executionIntentRepository;
  @Autowired private Web3TransactionJpaRepository transactionRepository;

  @Test
  @DisplayName("expired admin preparation claim query는 FOR UPDATE SKIP LOCKED를 사용한다")
  void findExpiredAdminPreparingAttemptsWithLockUsesSkipLocked() throws Exception {
    Method method =
        MarketplaceReservationActionStateJpaRepository.class.getDeclaredMethod(
            "findExpiredAdminPreparingAttemptsWithLock", LocalDateTime.class, int.class);
    Query query = method.getAnnotation(Query.class);

    assertThat(query).isNotNull();
    assertThat(query.nativeQuery()).isTrue();
    assertThat(query.value().toLowerCase()).contains("for update skip locked");
  }

  @Test
  @DisplayName(
      "admin reconciliation 후보 query는 terminal intent와 repairable transaction outcome을 포함한다")
  void findBoundAdminExecutionAttemptsForTerminalReplayIncludesRepairableTransactionOutcomes()
      throws Exception {
    Method method =
        MarketplaceReservationActionStateJpaRepository.class.getDeclaredMethod(
            "findBoundAdminExecutionAttemptsForTerminalReplay", LocalDateTime.class, int.class);
    Query query = method.getAnnotation(Query.class);

    assertThat(query).isNotNull();
    assertThat(query.nativeQuery()).isTrue();
    assertThat(query.value().toLowerCase()).contains("from web3_execution_intents");
    assertThat(query.value().toLowerCase()).contains("left join web3_transactions");
    assertThat(query.value().toLowerCase()).contains("for update skip locked");
    assertThat(query.value()).contains("'CONFIRMED'");
    assertThat(query.value()).contains("'SUCCEEDED'");
    assertThat(query.value()).contains("'FAILED_ONCHAIN'");
    assertThat(query.value()).contains("'SIGNED'");
    assertThat(query.value()).contains("'PENDING_ONCHAIN'");
  }

  @Test
  @DisplayName("admin reconciliation claim query는 실제 DB에서 stale claim과 repairable tx만 선별한다")
  void findBoundAdminExecutionAttemptsForTerminalReplaySelectsEligibleRowsFromDatabase() {
    LocalDateTime base = LocalDateTime.of(2026, 5, 23, 12, 0);
    LocalDateTime claimStaleBefore = base.minusMinutes(5);
    saveState("intent-stale-claim", "ADMIN_REFUND", "RECONCILING", base.minusMinutes(10));
    saveIntent(
        "intent-stale-claim", ExecutionIntentStatus.FAILED_ONCHAIN, null, base.minusMinutes(10));
    saveState("intent-terminal", "ADMIN_REFUND", null, base.minusMinutes(4));
    saveIntent("intent-terminal", ExecutionIntentStatus.CONFIRMED, null, base.minusMinutes(4));
    saveState("intent-repair-succeeded", "ADMIN_SETTLE", null, base.minusMinutes(3));
    saveIntent(
        "intent-repair-succeeded",
        ExecutionIntentStatus.PENDING_ONCHAIN,
        saveTx("tx-repair-succeeded", Web3TxStatus.SUCCEEDED),
        base.minusMinutes(3));
    saveState("intent-repair-failed", "ADMIN_REFUND", null, base.minusMinutes(2));
    saveIntent(
        "intent-repair-failed",
        ExecutionIntentStatus.SIGNED,
        saveTx("tx-repair-failed", Web3TxStatus.FAILED_ONCHAIN),
        base.minusMinutes(2));
    saveState("intent-fresh-claim", "ADMIN_REFUND", "RECONCILING", base.minusMinutes(1));
    saveIntent(
        "intent-fresh-claim", ExecutionIntentStatus.FAILED_ONCHAIN, null, base.minusMinutes(1));
    saveState("intent-pending-tx", "ADMIN_REFUND", null, base.minusSeconds(30));
    saveIntent(
        "intent-pending-tx",
        ExecutionIntentStatus.SIGNED,
        saveTx("tx-pending", Web3TxStatus.PENDING),
        base.minusSeconds(30));
    saveState("intent-user-action", "BUYER_CANCEL", null, base.minusSeconds(20));
    saveIntent("intent-user-action", ExecutionIntentStatus.CONFIRMED, null, base.minusSeconds(20));

    List<MarketplaceReservationActionStateEntity> result =
        actionStateRepository.findBoundAdminExecutionAttemptsForTerminalReplay(
            claimStaleBefore, 10);

    assertThat(result)
        .extracting(MarketplaceReservationActionStateEntity::getExecutionIntentPublicId)
        .containsExactly(
            "intent-stale-claim",
            "intent-terminal",
            "intent-repair-succeeded",
            "intent-repair-failed");
  }

  private void saveState(
      String intentPublicId, String actionType, String errorCode, LocalDateTime updatedAt) {
    actionStateRepository.saveAndFlush(
        MarketplaceReservationActionStateEntity.builder()
            .reservationId((long) Math.abs(intentPublicId.hashCode()))
            .escrowId((long) Math.abs(intentPublicId.hashCode()) + 10_000L)
            .actionType(actionType)
            .actorType(actionType.startsWith("ADMIN") ? "ADMIN" : "BUYER")
            .actorUserId(actionType.startsWith("ADMIN") ? 77L : 7L)
            .requestSource(actionType.startsWith("ADMIN") ? "MANUAL_ADMIN" : "USER")
            .attemptNo(1)
            .attemptToken("attempt-" + intentPublicId)
            .executionIntentPublicId(intentPublicId)
            .status("INTENT_BOUND")
            .reasonCode(actionType.startsWith("ADMIN") ? "TRAINER_TIMEOUT" : null)
            .retryable(false)
            .errorCode(errorCode)
            .createdAt(updatedAt)
            .updatedAt(updatedAt)
            .build());
  }

  private Long saveTx(String key, Web3TxStatus status) {
    return transactionRepository
        .saveAndFlush(
            Web3TransactionEntity.builder()
                .idempotencyKey(key)
                .referenceType(Web3ReferenceType.SERVER_TO_USER)
                .referenceId(key)
                .fromAddress("0x" + "1".repeat(40))
                .toAddress("0x" + "2".repeat(40))
                .amountWei(BigInteger.ONE)
                .txType(Web3TxType.EIP1559)
                .status(status)
                .txHash("0x" + "a".repeat(64))
                .createdAt(LocalDateTime.of(2026, 5, 23, 11, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 23, 11, 0))
                .build())
        .getId();
  }

  private void saveIntent(
      String publicId, ExecutionIntentStatus status, Long submittedTxId, LocalDateTime createdAt) {
    executionIntentRepository.saveAndFlush(
        Web3ExecutionIntentEntity.builder()
            .publicId(publicId)
            .rootIdempotencyKey("root-" + publicId)
            .attemptNo(1)
            .resourceType(ExecutionResourceType.ORDER)
            .resourceId("1")
            .actionType(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)
            .requesterUserId(1L)
            .mode(ExecutionMode.EIP1559)
            .status(status)
            .payloadHash("0x" + "b".repeat(64))
            .payloadSnapshotJson("{\"payload\":true}")
            .authorityAddress("0x" + "3".repeat(40))
            .authorityNonce(1L)
            .delegateTarget("0x" + "4".repeat(40))
            .expiresAt(createdAt.plusMinutes(5))
            .authorizationPayloadHash("0x" + "5".repeat(64))
            .executionDigest("0x" + "6".repeat(64))
            .reservedSponsorCostWei(BigInteger.ZERO)
            .sponsorUsageDateKst(LocalDate.of(2026, 5, 23))
            .submittedTxId(submittedTxId)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());
  }
}
