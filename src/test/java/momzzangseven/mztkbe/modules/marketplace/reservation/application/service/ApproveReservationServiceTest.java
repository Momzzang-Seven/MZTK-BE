package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApproveReservationService 단위 테스트")
class ApproveReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant FIXED_NOW = Instant.parse("2025-06-01T03:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZONE);

  private ApproveReservationService sut;

  private static final Long RESERVATION_ID = 1L;
  private static final Long TRAINER_ID = 100L;
  private static final Long OTHER_TRAINER_ID = 999L;

  @BeforeEach
  void setUp() {
    sut = new ApproveReservationService(loadReservationPort, saveReservationPort, FIXED_CLOCK);
    sut.setTransactionPort(ReservationTestTransactionPort.direct());
  }

  private Reservation pendingReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(1L)
        .trainerId(TRAINER_ID)
        .slotId(1L)
        .reservationDate(LocalDate.now().plusDays(3))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderId("order-1")
        .version(0L)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[AP-01] 본인 예약 승인 시 APPROVED 상태 반환")
    void 정상_승인() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));
      Reservation approvedRes = pendingReservation().approve();
      given(saveReservationPort.save(any())).willReturn(approvedRes);

      // when
      ApproveReservationResult result =
          sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID));

      // then
      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.APPROVED);
      assertThat(result.businessStatus()).isEqualTo(ReservationStatus.APPROVED);
      then(saveReservationPort).should().save(any());
    }

    @Test
    @DisplayName("[AP-07] USER_EIP7702 예약은 split escrow LOCKED와 active action 없음이 확인되면 승인된다")
    void split_escrow_locked_without_active_action_approval_succeeds() {
      ApproveReservationService splitSut =
          new ApproveReservationService(
              loadReservationPort,
              saveReservationPort,
              loadReservationEscrowPort,
              loadReservationActionStatePort,
              FIXED_CLOCK);
      splitSut.setTransactionPort(ReservationTestTransactionPort.direct());
      Reservation pending = pendingReservation();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(pending));
      given(loadReservationEscrowPort.findByReservationIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(lockedEscrow()));
      given(loadReservationActionStatePort.findByReservationIdAndStatuses(any(), any()))
          .willReturn(List.of());
      given(saveReservationPort.save(any())).willReturn(pending.approve());

      ApproveReservationResult result =
          splitSut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID));

      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.APPROVED);
      assertThat(result.businessStatus()).isEqualTo(ReservationStatus.APPROVED);
      then(loadReservationEscrowPort).should().findByReservationIdWithLock(RESERVATION_ID);
      then(loadReservationActionStatePort)
          .should()
          .findByReservationIdAndStatuses(
              RESERVATION_ID,
              List.of(
                  ReservationActionStateStatus.PREPARING,
                  ReservationActionStateStatus.INTENT_BOUND));
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[AP-02] 타인 트레이너가 승인 시도 시 MARKETPLACE_UNAUTHORIZED_ACCESS 예외")
    void 타인_트레이너_승인_시도() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, OTHER_TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_UNAUTHORIZED_ACCESS.getCode()));
    }

    @Test
    @DisplayName("[AP-03] APPROVED 상태 예약 재승인 시 MARKETPLACE_RESERVATION_INVALID_STATUS 예외")
    void 이미_승인된_예약_재승인() {
      // given: 이미 APPROVED 상태
      Reservation approved = pendingReservation().approve();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(approved));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
    }

    @Test
    @DisplayName("[AP-04] contract deadline 이후에는 승인할 수 없다")
    void contract_deadline_이후_승인_차단() {
      Reservation expired =
          pendingReservation().toBuilder()
              .contractDeadlineAt(LocalDateTime.ofInstant(FIXED_NOW, ZONE).minusMinutes(1))
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(expired));

      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[AP-05] 생성 후 72시간이 지난 PENDING 예약은 승인할 수 없다")
    void timeout_window_이후_승인_차단() {
      Reservation timedOut =
          pendingReservation().toBuilder()
              .createdAt(LocalDateTime.ofInstant(FIXED_NOW, ZONE).minusHours(72))
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(timedOut));

      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[AP-06] 세션 시작 1시간 이내 PENDING 예약은 승인할 수 없다")
    void session_guard_window_승인_차단() {
      LocalDateTime sessionStart = LocalDateTime.ofInstant(FIXED_NOW, ZONE).plusMinutes(30);
      Reservation withinSessionGuard =
          pendingReservation().toBuilder()
              .reservationDate(sessionStart.toLocalDate())
              .reservationTime(sessionStart.toLocalTime())
              .createdAt(LocalDateTime.ofInstant(FIXED_NOW, ZONE))
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(withinSessionGuard));

      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[AP-08] active action-state가 있으면 trainer approval을 차단한다")
    void active_action_state_blocks_approval() {
      ApproveReservationService splitSut =
          new ApproveReservationService(
              loadReservationPort,
              saveReservationPort,
              loadReservationEscrowPort,
              loadReservationActionStatePort,
              FIXED_CLOCK);
      splitSut.setTransactionPort(ReservationTestTransactionPort.direct());
      Reservation pending = pendingReservation();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(pending));
      given(loadReservationEscrowPort.findByReservationIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(lockedEscrow()));
      given(loadReservationActionStatePort.findByReservationIdAndStatuses(any(), any()))
          .willReturn(List.of(activeCancelActionState()));

      assertThatThrownBy(
              () -> splitSut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
    }
  }

  private MarketplaceReservationEscrow lockedEscrow() {
    return MarketplaceReservationEscrow.builder()
        .id(11L)
        .reservationId(RESERVATION_ID)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .orderKey("0x" + "0".repeat(63) + "1")
        .buyerWalletAddress("0x1111111111111111111111111111111111111111")
        .trainerWalletAddress("0x2222222222222222222222222222222222222222")
        .contractDeadlineAt(LocalDateTime.ofInstant(FIXED_NOW, ZONE).plusDays(1))
        .build();
  }

  private MarketplaceReservationActionState activeCancelActionState() {
    return MarketplaceReservationActionState.builder()
        .id(21L)
        .reservationId(RESERVATION_ID)
        .escrowId(11L)
        .actionType(ReservationEscrowAction.BUYER_CANCEL)
        .actorType(ReservationEscrowActorType.BUYER)
        .actorUserId(1L)
        .attemptNo(1)
        .attemptToken("cancel-token")
        .status(ReservationActionStateStatus.PREPARING)
        .build();
  }
}
