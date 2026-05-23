package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CalculateMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalculateMarketplaceAdminRefundReviewServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;

  @Mock
  private LoadMarketplaceAdminExecutionAuthorityPort loadMarketplaceAdminExecutionAuthorityPort;

  private CalculateMarketplaceAdminRefundReviewService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC);
    service =
        new CalculateMarketplaceAdminRefundReviewService(
            loadReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort,
            loadReservationExecutionStatePort,
            clock);
  }

  @Test
  @DisplayName("refund review returns server-relayer-only authority and reason-specific options")
  void refundReviewReturnsReasonOptions() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.empty());

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, true));

    assertThat(result.processable()).isTrue();
    assertThat(result.authority().requiresUserSignature()).isFalse();
    assertThat(result.authority().authorityModel())
        .isEqualTo(MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY);
    assertThat(result.authority().canManualRefund()).isTrue();
    assertThat(result.reasonOptions()).hasSize(3);
    assertThat(result.reasonOptions())
        .extracting("reasonCode")
        .containsExactly("TRAINER_TIMEOUT", "SESSION_START_WINDOW_TIMEOUT", "ADMIN_MANUAL_REFUND");
    assertThat(result.reasonOptions().get(2).processable()).isTrue();
    assertThat(result.reasonOptions().get(2).blockingCode()).isNull();
    assertThat(result.reasonOptions().get(2).validationItems())
        .extracting("code")
        .containsExactly(MarketplaceAdminReviewValidationCode.MANUAL_REFUND_CONFIRMATION_REQUIRED);
    assertThat(result.reasonOptions().get(2).requiresConfirmation()).isTrue();
    assertThat(result.reasonOptions().get(2).authoritySatisfied()).isTrue();
  }

  @Test
  @DisplayName("refund review blocks active local execution and exposes blocking code")
  void refundReviewBlocksActiveExecution() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.of(activeAttempt()));

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, false));

    assertThat(result.processable()).isFalse();
    assertThat(result.baseBlockingCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.ACTIVE_EXECUTION_EXISTS);
    assertThat(result.activeExecution()).isNotNull();
  }

  @Test
  @DisplayName(
      "shared intent confirmed but local hook lagging이면 confirmed pending local sync phase를 노출한다")
  void refundReviewExposesConfirmedPendingLocalSync() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.of(intentBoundAttempt()));
    given(loadReservationExecutionStatePort.loadState("intent-1"))
        .willReturn(
            new ReservationExecutionStateView(
                "intent-1",
                "CONFIRMED",
                "MARKETPLACE_ADMIN_REFUND",
                10L,
                55L,
                "SUCCEEDED",
                "0xhash"));

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, false));

    assertThat(result.adminExecutionPhase())
        .isEqualTo(MarketplaceAdminExecutionPhase.CONFIRMED_PENDING_LOCAL_SYNC);
    assertThat(result.activeExecution().executionStatus()).isEqualTo("CONFIRMED");
    assertThat(result.activeExecution().txHash()).isEqualTo("0xhash");
  }

  @Test
  @DisplayName("refund review preflight exposes chain check time and server signer blocking")
  void refundReviewPreflightBlocksUnavailableServerSigner() {
    service =
        new CalculateMarketplaceAdminRefundReviewService(
            loadReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort,
            loadReservationExecutionStatePort,
            loadReservationEscrowOrderPort,
            loadMarketplaceAdminExecutionAuthorityPort,
            Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC));
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.empty());
    given(loadReservationEscrowOrderPort.getOrder("0xorder")).willReturn(createdOrder());
    given(loadMarketplaceAdminExecutionAuthorityPort.load())
        .willReturn(MarketplaceAdminExecutionAuthorityView.serverRelayerOnly());

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, true));

    assertThat(result.processable()).isFalse();
    assertThat(result.chainCheckedAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 12, 0));
    assertThat(result.baseBlockingCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.SERVER_SIGNER_UNAVAILABLE);
    assertThat(result.baseValidationItems())
        .extracting("code")
        .contains(MarketplaceAdminReviewValidationCode.SERVER_SIGNER_UNAVAILABLE);
  }

  @Test
  @DisplayName("refund review separates relayer check failure from unregistered relayer")
  void refundReviewPreflightBlocksRelayerCheckFailure() {
    service =
        new CalculateMarketplaceAdminRefundReviewService(
            loadReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort,
            loadReservationExecutionStatePort,
            loadReservationEscrowOrderPort,
            loadMarketplaceAdminExecutionAuthorityPort,
            Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC));
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.empty());
    given(loadReservationEscrowOrderPort.getOrder("0xorder")).willReturn(createdOrder());
    given(loadMarketplaceAdminExecutionAuthorityPort.load())
        .willReturn(
            new MarketplaceAdminExecutionAuthorityView(
                false,
                MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY,
                true,
                "0x1111111111111111111111111111111111111111",
                false,
                MarketplaceAdminExecutionAuthorityView.RELAYER_REGISTRATION_CHECK_FAILED,
                false,
                false));

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, false));

    assertThat(result.processable()).isFalse();
    assertThat(result.baseBlockingCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.RELAYER_REGISTRATION_CHECK_FAILED);
    assertThat(result.authority().relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityView.RELAYER_REGISTRATION_CHECK_FAILED);
  }

  @Test
  @DisplayName("Phase C stale attempt is exposed as idle retry surface, not manual chain sync")
  void refundReviewMapsPhaseCStaleAttemptToIdlePhase() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(
            Optional.of(
                closedAttempt(
                    ReservationActionStateStatus.STALE,
                    MarketplaceAdminReviewValidationCode.IDEMPOTENCY_CONFLICT.name())));

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, true));

    assertThat(result.adminExecutionPhase()).isEqualTo(MarketplaceAdminExecutionPhase.IDLE);
    assertThat(result.lastAttempt()).isNotNull();
    assertThat(result.lastAttempt().failureStage().name()).isEqualTo("PHASE_C_BIND");
  }

  @Test
  @DisplayName("authoritative chain stale attempt remains manual sync in polling UX")
  void refundReviewMapsChainMismatchStaleAttemptToManualSyncPhase() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(
            Optional.of(
                closedAttempt(
                    ReservationActionStateStatus.STALE,
                    MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT.name())));

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, true));

    assertThat(result.adminExecutionPhase())
        .isEqualTo(MarketplaceAdminExecutionPhase.MANUAL_SYNC_REQUIRED);
    assertThat(result.lastAttempt()).isNotNull();
  }

  @Test
  @DisplayName("expired/canceled/nonce-stale terminated attempts use EXPIRED polling phase")
  void refundReviewMapsNonceStaleTerminatedAttemptToExpiredPhase() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(lockedPending()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(
            Optional.of(closedAttempt(ReservationActionStateStatus.TERMINATED, "NONCE_STALE")));

    var result = service.execute(new CalculateMarketplaceAdminRefundReviewQuery(1L, true));

    assertThat(result.adminExecutionPhase()).isEqualTo(MarketplaceAdminExecutionPhase.EXPIRED);
    assertThat(result.lastAttempt()).isNotNull();
  }

  private Reservation lockedPending() {
    return Reservation.builder()
        .id(1L)
        .userId(10L)
        .trainerId(20L)
        .slotId(30L)
        .reservationDate(LocalDate.of(2026, 5, 21))
        .reservationTime(LocalTime.of(23, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderKey("0xorder")
        .buyerWalletAddress("0xbuyer")
        .trainerWalletAddress("0xtrainer")
        .tokenAddress("0xtoken")
        .priceBaseUnits("1000000000000000000")
        .createdAt(LocalDateTime.of(2026, 5, 18, 11, 0))
        .version(4L)
        .build();
  }

  private ReservationEscrowOrderView createdOrder() {
    return new ReservationEscrowOrderView(
        "0xorder",
        "1000000000000000000",
        "0xtoken",
        Instant.parse("2026-05-22T12:00:00Z").getEpochSecond(),
        ReservationEscrowOrderView.STATE_CREATED,
        "0xbuyer",
        "0xtrainer");
  }

  private MarketplaceReservationActionState activeAttempt() {
    return MarketplaceReservationActionState.builder()
        .id(99L)
        .reservationId(1L)
        .status(ReservationActionStateStatus.PREPARING)
        .build();
  }

  private MarketplaceReservationActionState intentBoundAttempt() {
    return MarketplaceReservationActionState.builder()
        .id(99L)
        .reservationId(1L)
        .status(ReservationActionStateStatus.INTENT_BOUND)
        .executionIntentPublicId("intent-1")
        .build();
  }

  private MarketplaceReservationActionState closedAttempt(
      ReservationActionStateStatus status, String errorCode) {
    return MarketplaceReservationActionState.builder()
        .id(99L)
        .reservationId(1L)
        .status(status)
        .errorCode(errorCode)
        .retryable(false)
        .build();
  }
}
