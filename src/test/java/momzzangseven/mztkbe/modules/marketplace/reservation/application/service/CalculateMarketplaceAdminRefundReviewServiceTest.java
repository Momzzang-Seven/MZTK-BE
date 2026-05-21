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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
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
    assertThat(result.reasonOptions()).hasSize(3);
    assertThat(result.reasonOptions())
        .extracting("reasonCode")
        .containsExactly("TRAINER_TIMEOUT", "SESSION_START_WINDOW_TIMEOUT", "ADMIN_MANUAL_REFUND");
    assertThat(result.reasonOptions().get(2).blockingCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.MANUAL_REFUND_CONFIRMATION_REQUIRED);
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
        .buyerWalletAddress("0xbuyer")
        .trainerWalletAddress("0xtrainer")
        .tokenAddress("0xtoken")
        .priceBaseUnits("1000000000000000000")
        .createdAt(LocalDateTime.of(2026, 5, 18, 11, 0))
        .version(4L)
        .build();
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
}
