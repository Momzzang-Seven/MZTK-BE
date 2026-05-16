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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompleteReservationService 단위 테스트")
class CompleteReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  @Mock private CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  @Mock private LoadReservationWalletPort loadReservationWalletPort;
  @Mock private LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  /**
   * Fixed clock pointing to 2025-06-01T12:00:00 KST.
   *
   * <p>Used as "now" for all time-comparison checks. Reservation dates are defined relative to this
   * fixed point so tests are deterministic regardless of actual wall-clock time.
   */
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private static final Instant FIXED_NOW = Instant.parse("2025-06-01T03:00:00Z"); // 12:00 KST
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZONE);

  // "now" as seen by the service = 2025-06-01 12:00 KST
  private static final LocalDate TODAY = LocalDate.of(2025, 6, 1);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);

  private CompleteReservationService sut;

  private static final Long RESERVATION_ID = 1L;
  private static final Long USER_ID = 1L;
  private static final Long OTHER_USER_ID = 999L;
  private static final String ORDER_ID = "123e4567-e89b-12d3-a456-426614174000";

  @BeforeEach
  void setUp() {
    // Explicit constructor injection so Clock is deterministic in tests.
    sut =
        new CompleteReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            FIXED_CLOCK);
  }

  /** APPROVED 예약, 수업 시간은 이미 과거(어제). */
  private Reservation approvedPastReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(USER_ID)
        .trainerId(100L)
        .slotId(1L)
        .reservationDate(YESTERDAY)
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.APPROVED)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderId(ORDER_ID)
        .bookedPriceAmount(50_000)
        .version(0L)
        .build();
  }

  /** APPROVED 예약, 수업 시간은 미래(내일). */
  private Reservation approvedFutureReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(USER_ID)
        .trainerId(100L)
        .slotId(1L)
        .reservationDate(TOMORROW)
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.APPROVED)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderId(ORDER_ID)
        .bookedPriceAmount(50_000)
        .version(0L)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[CM-01] 수업 종료 후 완료 확인 시 CONFIRM_PENDING 및 Web3 실행 정보를 반환")
    void 수업_종료_후_완료() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(approvedPastReservation()))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationWalletPort.loadActiveWalletAddress(any()))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18));
      given(prepareReservationEscrowExecutionPort.prepareConfirm(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      // when
      CompleteReservationResult result =
          sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRM_PENDING);
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().actionType()).isEqualTo("MARKETPLACE_CLASS_CONFIRM");
      then(eventPublisher).shouldHaveNoInteractions();
    }
  }

  private ReservationExecutionWriteView web3() {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
        "MARKETPLACE_CLASS_CONFIRM",
        "0x" + "0".repeat(63) + "1",
        new ReservationExecutionWriteView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.now(FIXED_CLOCK).plusMinutes(5), 300L),
        new ReservationExecutionWriteView.Execution("EIP7702", 1),
        null,
        null,
        false,
        null,
        null);
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[CM-02] 수업 시작 전 완료 시도 시 MARKETPLACE_RESERVATION_EARLY_COMPLETE 예외")
    void 수업_시작_전_완료() {
      // given — 내일 수업이므로 fixed clock 기준 미래
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(approvedFutureReservation()));

      // when & then
      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_EARLY_COMPLETE.getCode()));
    }

    @Test
    @DisplayName("[CM-03] 타인이 완료 시도 시 MARKETPLACE_UNAUTHORIZED_ACCESS 예외")
    void 타인_완료_시도() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(approvedPastReservation()));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, OTHER_USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_UNAUTHORIZED_ACCESS.getCode()));
    }

    @Test
    @DisplayName("[CM-04] PENDING 상태 예약 완료 시도 시 MARKETPLACE_RESERVATION_INVALID_STATUS 예외")
    void PENDING_상태_완료_시도() {
      // given: PENDING → SETTLED 전이는 불가
      Reservation pending =
          Reservation.builder()
              .id(RESERVATION_ID)
              .userId(USER_ID)
              .trainerId(100L)
              .slotId(1L)
              .reservationDate(YESTERDAY)
              .reservationTime(LocalTime.of(10, 0))
              .durationMinutes(60)
              .status(ReservationStatus.PENDING)
              .orderId(ORDER_ID)
              .bookedPriceAmount(50_000)
              .version(0L)
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(pending));

      // when & then
      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
    }

    @Test
    @DisplayName("[CM-05] Phase B 바인딩 실패 시 signable intent를 취소하고 APPROVED로 롤백한다")
    void phase_b_바인딩_실패_보상() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(approvedPastReservation()))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(current.toBuilder().status(ReservationStatus.APPROVED).build());
              })
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationWalletPort.loadActiveWalletAddress(any()))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18));
      given(prepareReservationEscrowExecutionPort.prepareConfirm(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(true);

      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

      then(cancelReservationEscrowExecutionPort)
          .should()
          .cancelSignableIntent(
              org.mockito.ArgumentMatchers.eq("intent-1"),
              org.mockito.ArgumentMatchers.eq("MARKETPLACE_PHASE_B_BIND_FAILED"),
              any());
      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.APPROVED);
    }

    @Test
    @DisplayName("[CM-06] Phase B 보상에서 intent 취소가 불가하면 pending 상태를 즉시 롤백하지 않는다")
    void phase_b_보상_취소_불가_시_즉시_롤백하지_않음() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(approvedPastReservation()))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(current.toBuilder().status(ReservationStatus.APPROVED).build());
              });
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationWalletPort.loadActiveWalletAddress(any()))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18));
      given(prepareReservationEscrowExecutionPort.prepareConfirm(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(false);

      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class);

      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.CONFIRM_PENDING);
    }

    @Test
    @DisplayName("[CM-07] legacy dispatch 예약은 사용자 EIP-7702 완료 준비로 진입할 수 없다")
    void legacy_dispatch_예약_완료_차단() {
      Reservation legacy =
          approvedPastReservation().toBuilder()
              .escrowFlow(ReservationEscrowFlow.LEGACY_DISPATCH)
              .escrowStatus(ReservationEscrowStatus.NONE)
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(legacy));

      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));

      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }
  }
}
