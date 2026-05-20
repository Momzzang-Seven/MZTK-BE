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
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
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
@DisplayName("CancelPendingReservationService 단위 테스트")
class CancelPendingReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  @Mock private CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  @Mock private LoadReservationWalletPort loadReservationWalletPort;
  @Mock private LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant FIXED_NOW = Instant.parse("2025-06-01T03:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZONE);

  private CancelPendingReservationService sut;

  private static final Long RESERVATION_ID = 1L;
  private static final Long USER_ID = 50L;
  private static final Long OTHER_USER_ID = 999L;
  private static final String ORDER_ID = "123e4567-e89b-12d3-a456-426614174000";

  @BeforeEach
  void setUp() {
    sut =
        new CancelPendingReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            FIXED_CLOCK);
    sut.setTransactionPort(ReservationTestTransactionPort.direct());
  }

  private Reservation pendingReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(USER_ID)
        .trainerId(10L)
        .slotId(1L)
        .reservationDate(LocalDate.now().plusDays(2))
        .reservationTime(LocalTime.of(14, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
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
    @DisplayName("[CP-01] PENDING 예약을 본인이 취소하면 CANCEL_PENDING 및 Web3 실행 정보를 반환")
    void 정상_취소() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()))
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
      given(prepareReservationEscrowExecutionPort.prepareCancel(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      // when
      CancelPendingReservationResult result =
          sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID));

      // then
      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.CANCEL_PENDING);
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().actionType()).isEqualTo("MARKETPLACE_CLASS_CANCEL");
      then(saveReservationPort).should(org.mockito.Mockito.times(2)).save(any());
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CP-02] 기존 active action-state가 있으면 새 cancel intent를 만들지 않는다")
    void active_action_state_blocks_new_cancel() {
      CancelPendingReservationService guardedSut =
          new CancelPendingReservationService(
              loadReservationPort,
              saveReservationPort,
              prepareReservationEscrowExecutionPort,
              cancelReservationEscrowExecutionPort,
              loadReservationWalletPort,
              loadReservationEscrowPaymentConfigPort,
              null,
              null,
              loadReservationActionStatePort,
              null,
              loadReservationExecutionStatePort,
              loadReservationExecutionCandidatePort,
              FIXED_CLOCK);
      guardedSut.setTransactionPort(ReservationTestTransactionPort.direct());
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));
      given(
              loadReservationActionStatePort.findByReservationIdAndStatuses(
                  RESERVATION_ID,
                  List.of(
                      ReservationActionStateStatus.PREPARING,
                      ReservationActionStateStatus.INTENT_BOUND)))
          .willReturn(
              List.of(
                  MarketplaceReservationActionState.builder()
                      .id(9L)
                      .reservationId(RESERVATION_ID)
                      .actionType(ReservationEscrowAction.TRAINER_REJECT)
                      .status(ReservationActionStateStatus.INTENT_BOUND)
                      .executionIntentPublicId("intent-reject")
                      .build()));

      assertThatThrownBy(
              () ->
                  guardedSut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e ->
                  assertThat(e.getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }
  }

  private ReservationExecutionWriteView web3() {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
        "MARKETPLACE_CLASS_CANCEL",
        "0x" + "0".repeat(63) + "1",
        new ReservationExecutionWriteView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.now().plusMinutes(5), 300L),
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
    @DisplayName("[CP-02] 존재하지 않는 예약 ID로 요청 시 MARKETPLACE_RESERVATION_NOT_FOUND 예외")
    void 존재하지_않는_예약() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("[CP-03] 타인 예약 취소 시도 시 MarketplaceUnauthorizedAccessException 예외")
    void 타인_예약_취소_시도() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, OTHER_USER_ID)))
          .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("[CP-05] Phase B 바인딩 실패 시 signable intent를 취소하고 pending 상태를 롤백한다")
    void phase_b_바인딩_실패_보상() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(current.toBuilder().status(ReservationStatus.PENDING).build());
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
      given(prepareReservationEscrowExecutionPort.prepareCancel(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(true);

      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
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
      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("[CP-06] Phase B 보상에서 intent 취소가 불가하면 pending 상태를 즉시 롤백하지 않는다")
    void phase_b_보상_취소_불가_시_즉시_롤백하지_않음() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(current.toBuilder().status(ReservationStatus.PENDING).build());
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
      given(prepareReservationEscrowExecutionPort.prepareCancel(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(false);

      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class);

      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
    }

    @Test
    @DisplayName("[CP-04] APPROVED 상태 예약 취소 시도 시 MARKETPLACE_RESERVATION_INVALID_STATUS 예외")
    void 승인된_예약_취소_시도() {
      // given: PENDING → APPROVED 전환된 예약
      Reservation approved = pendingReservation().approve();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(approved));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
    }

    @Test
    @DisplayName("[CP-07] contract deadline 이후에는 취소 intent 대신 deadline refund required로 전환한다")
    void contract_deadline_이후_취소_차단_및_refund_required_전환() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      Reservation expired =
          pendingReservation().toBuilder()
              .contractDeadlineAt(LocalDateTime.ofInstant(FIXED_NOW, ZONE).minusMinutes(1))
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(expired));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });

      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED.getCode()));

      assertThat(latestSaved.get().getStatus())
          .isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CP-08] legacy dispatch 예약은 사용자 EIP-7702 취소 준비로 진입할 수 없다")
    void legacy_dispatch_예약_취소_차단() {
      Reservation legacy =
          pendingReservation().toBuilder()
              .escrowFlow(ReservationEscrowFlow.LEGACY_DISPATCH)
              .escrowStatus(ReservationEscrowStatus.NONE)
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(legacy));

      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));

      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }
  }
}
