package momzzangseven.mztkbe.integration.e2e.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationActionStateEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.MarketplaceReservationActionStateJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class MarketplaceAdminActionStateClaimConcurrencyE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private MarketplaceReservationActionStateJpaRepository actionStateRepository;

  @Test
  @DisplayName("PostgreSQL SKIP LOCKED claim은 동일 admin action state 중복 replay를 막는다")
  void claimQuerySkipLockedPreventsDuplicateConcurrentReplayClaim() throws Exception {
    Long actionStateId = insertClaimCandidate("intent-" + shortToken());
    CountDownLatch firstClaimLocked = new CountDownLatch(1);
    CountDownLatch releaseFirstClaim = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<List<Long>> first =
          executor.submit(
              () ->
                  requiresNewTransaction()
                      .execute(
                          ignored -> {
                            List<Long> ids = claimCandidateIds();
                            firstClaimLocked.countDown();
                            awaitLatch(releaseFirstClaim, "release first claim");
                            return ids;
                          }));
      awaitLatch(firstClaimLocked, "first claim lock");

      Future<List<Long>> second =
          executor.submit(() -> requiresNewTransaction().execute(ignored -> claimCandidateIds()));

      assertThat(second.get(5, TimeUnit.SECONDS)).isEmpty();
      releaseFirstClaim.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(actionStateId);
    } finally {
      releaseFirstClaim.countDown();
      executor.shutdownNow();
    }
  }

  private List<Long> claimCandidateIds() {
    return actionStateRepository
        .findBoundAdminExecutionAttemptsForTerminalReplay(LocalDateTime.of(2026, 5, 23, 11, 55), 10)
        .stream()
        .map(MarketplaceReservationActionStateEntity::getId)
        .toList();
  }

  private TransactionTemplate requiresNewTransaction() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setTimeout(10);
    return template;
  }

  private void awaitLatch(CountDownLatch latch, String label) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as(label).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for " + label, e);
    }
  }

  private Long insertClaimCandidate(String intentPublicId) {
    String orderKey = randomOrderKey();
    Long classId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO marketplace_classes (
                trainer_id, category, title, description, price_amount, duration_minutes
            ) VALUES (2, 'PT', ?, 'claim concurrency test', 100, 60)
            RETURNING id
            """,
            Long.class,
            "claim class " + shortToken());
    Long slotId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO class_slots (class_id, start_time, capacity)
            VALUES (?, TIME '10:00:00', 1)
            RETURNING id
            """,
            Long.class,
            classId);
    Long reservationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO class_reservations (
                user_id, trainer_id, class_slot_id, reservation_date, reservation_time,
                duration_minutes, status, escrow_status, escrow_flow, order_key
            ) VALUES (
                1, 2, ?, DATE '2026-05-20', TIME '10:00:00', 60, 'APPROVED',
                'LOCKED', 'USER_EIP7702', ?
            )
            RETURNING id
            """,
            Long.class,
            slotId,
            orderKey);
    Long escrowId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO marketplace_reservation_escrows (
                reservation_id, escrow_flow, escrow_status, order_key, buyer_wallet_address,
                trainer_wallet_address, token_address, price_base_units
            ) VALUES (
                ?, 'USER_EIP7702', 'LOCKED', ?, ?, ?, ?, 100
            )
            RETURNING id
            """,
            Long.class,
            reservationId,
            orderKey,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            "0x" + "3".repeat(40));
    jdbcTemplate.update(
        """
        INSERT INTO web3_execution_intents (
            public_id, root_idempotency_key, attempt_no, resource_type, resource_id,
            action_type, requester_user_id, mode, status, payload_hash, payload_snapshot_json,
            authority_address, authority_nonce, delegate_target, expires_at,
            authorization_payload_hash, execution_digest, reserved_sponsor_cost_wei,
            sponsor_usage_date_kst, created_at, updated_at
        ) VALUES (
            ?, ?, 1, 'ORDER', ?, 'MARKETPLACE_ADMIN_REFUND', 1, 'EIP1559', 'CONFIRMED',
            ?, '{"payload":true}', ?, 1, ?, TIMESTAMP '2026-05-23 12:10:00',
            ?, ?, 0, DATE '2026-05-23', TIMESTAMP '2026-05-23 11:50:00',
            TIMESTAMP '2026-05-23 11:50:00'
        )
        """,
        intentPublicId,
        "root-" + intentPublicId,
        reservationId.toString(),
        "0x" + "a".repeat(64),
        "0x" + "4".repeat(40),
        "0x" + "5".repeat(40),
        "0x" + "b".repeat(64),
        "0x" + "c".repeat(64));
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO marketplace_reservation_action_states (
            reservation_id, escrow_id, action_type, actor_type, actor_user_id, request_source,
            attempt_no, attempt_token, execution_intent_public_id, status, reason_code,
            retryable, created_at, updated_at
        ) VALUES (
            ?, ?, 'ADMIN_REFUND', 'ADMIN', 77, 'MANUAL_ADMIN', 1, ?, ?, 'INTENT_BOUND',
            'TRAINER_TIMEOUT', FALSE, TIMESTAMP '2026-05-23 11:50:00',
            TIMESTAMP '2026-05-23 11:50:00'
        )
        RETURNING id
        """,
        Long.class,
        reservationId,
        escrowId,
        "attempt-" + intentPublicId,
        intentPublicId);
  }

  private String randomOrderKey() {
    return "0x" + UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
  }

  private String shortToken() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
