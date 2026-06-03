package momzzangseven.mztkbe.integration.e2e.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3InternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RunMarketplaceWeb3AutoSettleResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.AutoSettleReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceWeb3InternalExecutionPolicyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.MarketplaceReservationActionStateJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler.MarketplaceWeb3AutoSettleScheduler;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository.Web3ExecutionIntentJpaRepository;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionAuthorityStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceInternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.BuildMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.LoadMarketplaceAdminExecutionAuthorityUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceInternalExecutionPolicyPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.execution.internal.enabled=true",
      "web3.execution.internal.action-policy=QNA_AND_MARKETPLACE_ADMIN",
      "web3.marketplace.admin.enabled=true",
      "web3.marketplace.admin.fail-fast=true",
      "web3.marketplace.admin.auto-settle.enabled=true",
      "web3.marketplace.admin.auto-settle.batch-size=10",
      "web3.marketplace.admin.auto-settle.scan-size=10",
      "web3.marketplace.admin.auto-settle.max-scan-pages-per-batch=2",
      "web3.marketplace.admin.auto-settle.max-batches-per-run=1",
    })
@DisplayName("[E2E] Marketplace Web3 auto-settle scheduler flow")
class MarketplaceWeb3AutoSettleSchedulerE2ETest extends E2ETestBase {

  private static final Instant NOW = Instant.parse("2026-05-29T03:00:00Z");
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AutoSettleReservationUseCase legacyAutoSettleReservationUseCase;
  @Autowired private MarketplaceWeb3AutoSettleScheduler scheduler;
  @Autowired private ReservationJpaRepository reservationJpaRepository;
  @Autowired private MarketplaceReservationActionStateJpaRepository actionStateRepository;
  @Autowired private Web3ExecutionIntentJpaRepository executionIntentRepository;

  @MockitoBean private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @MockitoBean private BuildMarketplaceAdminExecutionDraftUseCase buildDraftUseCase;

  @BeforeEach
  void setUp() {
    org.mockito.BDDMockito.given(loadReservationEscrowOrderPort.getOrder(anyString()))
        .willAnswer(
            invocation ->
                new ReservationEscrowOrderView(
                    invocation.getArgument(0),
                    "100",
                    wallet('3'),
                    NOW.plusSeconds(172_800).getEpochSecond(),
                    ReservationEscrowOrderView.STATE_CREATED,
                    wallet('1'),
                    wallet('2')));
    org.mockito.BDDMockito.willAnswer(invocation -> draft(invocation.getArgument(0)))
        .given(buildDraftUseCase)
        .execute(any());
  }

  @Test
  @DisplayName("legacy auto-settle은 USER_EIP7702 row를 건너뛰고 web3 auto-settle은 intent를 생성/바인딩한다")
  void schedulerQueuesAndBindsMarketplaceAdminSettleIntent() {
    Long reservationId = seedApprovedLockedUserEip7702Reservation();
    LocalDateTime localNow = LocalDateTime.ofInstant(NOW, APP_ZONE);

    int legacyProcessed = legacyAutoSettleReservationUseCase.runBatch(localNow);
    RunMarketplaceWeb3AutoSettleResult result = scheduler.runNow(NOW, "mkt-auto-settle-e2e-run-1");

    ReservationEntity reservation = reservationJpaRepository.findById(reservationId).orElseThrow();
    var actionStates =
        actionStateRepository.findLatestByReservationId(reservationId, PageRequest.of(0, 10));
    var actionState = actionStates.getFirst();
    var executionIntent =
        executionIntentRepository
            .findByPublicId(actionState.getExecutionIntentPublicId())
            .orElseThrow();

    assertThat(legacyProcessed).isZero();
    assertThat(result.batchesRun()).isEqualTo(1);
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.eligibleCount()).isEqualTo(1);
    assertThat(result.scheduledCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isZero();
    assertThat(result.failedCount()).isZero();

    assertThat(reservation.getStatus()).isEqualTo("ADMIN_SETTLE_PENDING");
    assertThat(reservation.getEscrowStatus()).isEqualTo("ADMIN_SETTLE_PENDING");
    assertThat(reservation.getCurrentExecutionIntentPublicId())
        .isEqualTo(actionState.getExecutionIntentPublicId());

    assertThat(actionStates).hasSize(1);
    assertThat(actionState.getActionType()).isEqualTo("ADMIN_SETTLE");
    assertThat(actionState.getActorType()).isEqualTo("SYSTEM");
    assertThat(actionState.getRequestSource()).isEqualTo("SCHEDULER");
    assertThat(actionState.getReasonCode()).isEqualTo("BUYER_CONFIRMATION_TIMEOUT");
    assertThat(actionState.getStatus()).isEqualTo("INTENT_BOUND");
    assertThat(actionState.getExecutionIntentPublicId()).isNotBlank();

    assertThat(executionIntent.getActionType())
        .isEqualTo(ExecutionActionType.MARKETPLACE_ADMIN_SETTLE);
    assertThat(executionIntent.getStatus()).isEqualTo(ExecutionIntentStatus.AWAITING_SIGNATURE);
    assertThat(executionIntent.getResourceType()).isEqualTo(ExecutionResourceType.ORDER);
    assertThat(executionIntent.getResourceId()).isEqualTo(String.valueOf(reservationId));
    assertThat(executionIntent.getRootIdempotencyKey())
        .isEqualTo(actionState.getRootIdempotencyKey());
  }

  private Long seedApprovedLockedUserEip7702Reservation() {
    String orderKey = randomOrderKey();
    String orderId = UUID.randomUUID().toString();
    Long classId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO marketplace_classes (
                trainer_id, category, title, description, price_amount, duration_minutes, active
            ) VALUES (?, 'PT', ?, 'auto-settle e2e fixture', 100, 60, true)
            RETURNING id
            """,
            Long.class,
            2L,
            "auto-settle-class-" + UUID.randomUUID().toString().substring(0, 8));
    Long slotId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO class_slots (class_id, start_time, capacity, active)
            VALUES (?, TIME '10:00:00', 1, true)
            RETURNING id
            """,
            Long.class,
            classId);
    jdbcTemplate.update(
        """
        INSERT INTO class_slot_days (slot_id, day_of_week)
        VALUES (?, 'WEDNESDAY')
        """,
        slotId);
    Long reservationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO class_reservations (
                user_id, trainer_id, class_slot_id, reservation_date, reservation_time,
                duration_minutes, status, escrow_status, escrow_flow, order_id, order_key,
                buyer_wallet_address, trainer_wallet_address, token_address, price_base_units,
                contract_deadline_epoch_seconds, contract_deadline_at, booked_price_amount
            ) VALUES (
                1, 2, ?, DATE '2026-05-28', TIME '10:00:00', 60, 'APPROVED', 'LOCKED',
                'USER_EIP7702', ?, ?, ?, ?, ?, 100, ?, ?, 100
            )
            RETURNING id
            """,
            Long.class,
            slotId,
            orderId,
            orderKey,
            wallet('1'),
            wallet('2'),
            wallet('3'),
            NOW.plusSeconds(172_800).getEpochSecond(),
            LocalDateTime.ofInstant(NOW.plusSeconds(172_800), APP_ZONE));
    jdbcTemplate.update(
        """
        INSERT INTO marketplace_reservation_escrows (
            reservation_id, escrow_flow, escrow_status, order_key, buyer_wallet_address,
            trainer_wallet_address, token_address, price_base_units, contract_deadline_epoch_seconds,
            contract_deadline_at
        ) VALUES (
            ?, 'USER_EIP7702', 'LOCKED', ?, ?, ?, ?, 100, ?, ?
        )
        """,
        reservationId,
        orderKey,
        wallet('1'),
        wallet('2'),
        wallet('3'),
        NOW.plusSeconds(172_800).getEpochSecond(),
        LocalDateTime.ofInstant(NOW.plusSeconds(172_800), APP_ZONE));
    return reservationId;
  }

  private MarketplaceExecutionDraft draft(MarketplaceAdminEscrowExecutionRequest request) {
    return new MarketplaceExecutionDraft(
        MarketplaceExecutionResourceType.ORDER,
        request.resourceId(),
        MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
        request.actionType(),
        request.requesterUserId(),
        request.counterpartyUserId(),
        request.orderId(),
        request.orderKey(),
        request.rootIdempotencyKey(),
        "0x" + "a".repeat(64),
        "{\"marketplace\":true}",
        List.of(new MarketplaceExecutionDraftCall(wallet('9'), BigInteger.ZERO, "0xdeadbeef")),
        true,
        null,
        null,
        null,
        null,
        new MarketplaceUnsignedTxSnapshot(
            8453L,
            wallet('4'),
            wallet('5'),
            BigInteger.ZERO,
            "0xdeadbeef",
            7L,
            BigInteger.valueOf(21_000L),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(3_000_000_000L)),
        "0x" + "b".repeat(64),
        null,
        null,
        LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));
  }

  private String randomOrderKey() {
    return "0x" + UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
  }

  private static String wallet(char ch) {
    return "0x" + String.valueOf(ch).repeat(40);
  }

  @Profile("integration")
  @org.springframework.boot.test.context.TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    Clock testAppClock() {
      return Clock.fixed(NOW, APP_ZONE);
    }

    @Bean
    @Primary
    LoadMarketplaceWeb3InternalExecutionPolicyPort
        testLoadMarketplaceWeb3InternalExecutionPolicyPort() {
      return () -> new MarketplaceWeb3InternalExecutionPolicyStatus(true, true, true);
    }

    @Bean
    @Primary
    LoadMarketplaceInternalExecutionPolicyPort testLoadMarketplaceInternalExecutionPolicyPort() {
      return () -> new MarketplaceInternalExecutionPolicyStatus(true, true, true);
    }

    @Bean
    @Primary
    LoadMarketplaceAdminExecutionAuthorityPort testLoadMarketplaceAdminExecutionAuthorityPort() {
      return () ->
          new MarketplaceAdminExecutionAuthorityView(
              false, "SERVER_RELAYER_ONLY", true, wallet('f'), true, false, false);
    }

    @Bean
    @Primary
    LoadMarketplaceAdminExecutionAuthorityUseCase
        testLoadMarketplaceAdminExecutionAuthorityUseCase() {
      return () ->
          new MarketplaceAdminExecutionAuthorityStatus(
              false,
              MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY,
              true,
              wallet('f'),
              true,
              MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_REGISTERED);
    }
  }
}
