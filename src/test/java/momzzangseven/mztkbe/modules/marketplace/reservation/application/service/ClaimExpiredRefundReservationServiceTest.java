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
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimExpiredRefundReservationService 단위 테스트")
class ClaimExpiredRefundReservationServiceTest {

  private static final Long RESERVATION_ID = 1L;
  private static final Long BUYER_ID = 50L;
  private static final Long TRAINER_ID = 100L;
  private static final Long OTHER_USER_ID = 999L;
  private static final String ORDER_ID = "123e4567-e89b-12d3-a456-426614174000";
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant FIXED_NOW = Instant.parse("2025-06-01T03:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZONE);

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  @Mock private CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  @Mock private LoadReservationWalletPort loadReservationWalletPort;
  @Mock private LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  @Mock private LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;

  private ClaimExpiredRefundReservationService sut;

  @BeforeEach
  void setUp() {
    sut =
        new ClaimExpiredRefundReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationExecutionWritePort,
            loadReservationExecutionStatePort,
            FIXED_CLOCK);
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[DR-01] 환불 가능 예약은 DEADLINE_REFUND_PENDING 및 Web3 실행 정보를 반환")
    void 환불_가능_예약_준비_성공() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(refundAvailableReservation()))
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
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      ClaimExpiredRefundReservationResult result =
          sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().actionType()).isEqualTo("MARKETPLACE_CLASS_EXPIRED_REFUND");
    }

    @Test
    @DisplayName("[DR-01C] 기존 cancel/reject intent가 진행 중이면 pointer를 지우지 않고 기존 intent를 반환한다")
    void 기존_current_intent가_진행중이면_기존_intent_반환() {
      Reservation reservation =
          pendingReservation(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1)).toBuilder()
              .status(ReservationStatus.CANCEL_PENDING)
              .currentExecutionIntentPublicId("cancel-intent-1")
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(reservation));
      given(loadReservationExecutionStatePort.loadState("cancel-intent-1"))
          .willReturn(
              state("MARKETPLACE_CLASS_CANCEL", "AWAITING_SIGNATURE", "cancel-intent-1", BUYER_ID));
      given(loadReservationExecutionWritePort.load(BUYER_ID, "cancel-intent-1"))
          .willReturn(web3("MARKETPLACE_CLASS_CANCEL", "AWAITING_SIGNATURE", "cancel-intent-1"));

      ClaimExpiredRefundReservationResult result =
          sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.CANCEL_PENDING);
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().existing()).isTrue();
      assertThat(result.web3().executionIntent().id()).isEqualTo("cancel-intent-1");
      then(saveReservationPort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[DR-01D] 기존 current intent가 retryable terminal이면 deadline refund intent를 준비한다")
    void 기존_current_intent가_retryable_terminal이면_refund_intent_준비() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      Reservation reservation =
          pendingReservation(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1)).toBuilder()
              .status(ReservationStatus.CANCEL_PENDING)
              .currentExecutionIntentPublicId("cancel-intent-1")
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(reservation))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationExecutionStatePort.loadState("cancel-intent-1"))
          .willReturn(
              state("MARKETPLACE_CLASS_CANCEL", "FAILED_ONCHAIN", "cancel-intent-1", BUYER_ID));
      given(loadReservationWalletPort.loadActiveWalletAddress(any()))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18));
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(
              new PrepareReservationEscrowResult(
                  web3(
                      "MARKETPLACE_CLASS_EXPIRED_REFUND",
                      "AWAITING_SIGNATURE",
                      "refund-intent-1")));

      ClaimExpiredRefundReservationResult result =
          sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      assertThat(result.web3()).isNotNull();
      assertThat(latestSaved.get().getCurrentExecutionIntentPublicId())
          .isEqualTo("refund-intent-1");
      then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
      then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "[DR-01E] trainer-owned reject intent가 retryable terminal이면 buyer deadline refund를 허용한다")
    void trainer_owned_reject_retryable_terminal이면_buyer_refund_준비() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      Reservation reservation =
          pendingReservation(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1)).toBuilder()
              .status(ReservationStatus.REJECT_PENDING)
              .currentExecutionIntentPublicId("reject-intent-1")
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(reservation))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationExecutionStatePort.loadState("reject-intent-1"))
          .willReturn(
              state("MARKETPLACE_CLASS_CANCEL", "FAILED_ONCHAIN", "reject-intent-1", TRAINER_ID));
      given(loadReservationWalletPort.loadActiveWalletAddress(any()))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18));
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(
              new PrepareReservationEscrowResult(
                  web3(
                      "MARKETPLACE_CLASS_EXPIRED_REFUND",
                      "AWAITING_SIGNATURE",
                      "refund-intent-1")));

      ClaimExpiredRefundReservationResult result =
          sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      assertThat(latestSaved.get().getCurrentExecutionIntentPublicId())
          .isEqualTo("refund-intent-1");
      then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
    }

    @Test
    @DisplayName("[DR-01F] deadline refund intent가 retryable terminal이면 새 refund intent로 교체한다")
    void deadline_refund_retryable_terminal이면_새_refund_intent로_교체() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      Reservation reservation =
          refundAvailableReservation().toBuilder()
              .status(ReservationStatus.DEADLINE_REFUND_PENDING)
              .currentExecutionIntentPublicId("old-refund-intent")
              .build();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(reservation))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationExecutionStatePort.loadState("old-refund-intent"))
          .willReturn(
              state(
                  "MARKETPLACE_CLASS_EXPIRED_REFUND",
                  "FAILED_ONCHAIN",
                  "old-refund-intent",
                  BUYER_ID));
      given(loadReservationWalletPort.loadActiveWalletAddress(any()))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18));
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(
              new PrepareReservationEscrowResult(
                  web3(
                      "MARKETPLACE_CLASS_EXPIRED_REFUND",
                      "AWAITING_SIGNATURE",
                      "new-refund-intent")));

      ClaimExpiredRefundReservationResult result =
          sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      assertThat(latestSaved.get().getCurrentExecutionIntentPublicId())
          .isEqualTo("new-refund-intent");
      then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @EnumSource(
        value = ReservationStatus.class,
        names = {"PENDING", "APPROVED"})
    @DisplayName("[DR-01B] 만료된 PENDING/APPROVED 예약은 refund available 전환 후 pending intent를 준비한다")
    void 만료된_예약은_refund_available_전환_후_pending_intent_준비(ReservationStatus status) {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(
              Optional.of(
                  pendingReservation(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1)).toBuilder()
                      .status(status)
                      .build()))
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
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      ClaimExpiredRefundReservationResult result =
          sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID));

      assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
      then(saveReservationPort).should(org.mockito.Mockito.times(3)).save(captor.capture());
      assertThat(captor.getAllValues().get(0).getStatus())
          .isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
      assertThat(captor.getAllValues().get(1).getStatus())
          .isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      assertThat(captor.getAllValues().get(2).getStatus())
          .isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
      then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[DR-02] 타인 예약 환불 시도 시 MARKETPLACE_UNAUTHORIZED_ACCESS 예외")
    void 타인_예약_환불_시도() {
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(refundAvailableReservation()));

      assertThatThrownBy(
              () ->
                  sut.execute(
                      new ClaimExpiredRefundReservationCommand(RESERVATION_ID, OTHER_USER_ID)))
          .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("[DR-03] 아직 deadline 전이면 refund intent를 만들지 않는다")
    void deadline_전_환불_차단() {
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(
              Optional.of(pendingReservation(LocalDateTime.now(FIXED_CLOCK).plusMinutes(1))));

      assertThatThrownBy(
              () -> sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(
                          ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED.getCode()));

      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[DR-03B] contract deadline과 같은 시각이면 refund intent를 만들지 않는다")
    void deadline_같은_시각_환불_차단() {
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation(LocalDateTime.now(FIXED_CLOCK))));

      assertThatThrownBy(
              () -> sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(
                          ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED.getCode()));

      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[DR-04] Phase B 바인딩 실패 시 signable intent를 취소하고 환불 가능 상태로 롤백한다")
    void phase_b_바인딩_실패_보상() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(refundAvailableReservation()))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(
                    current.toBuilder()
                        .status(ReservationStatus.DEADLINE_REFUND_AVAILABLE)
                        .build());
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
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(true);

      assertThatThrownBy(
              () -> sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID)))
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
      assertThat(latestSaved.get().getStatus())
          .isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
    }

    @Test
    @DisplayName("[DR-04B] 이미 deadline refund intent가 pending이면 중복 claim을 active conflict로 막는다")
    void 이미_deadline_refund_pending이면_중복_claim_차단() {
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(
              Optional.of(
                  refundAvailableReservation().toBuilder()
                      .status(ReservationStatus.DEADLINE_REFUND_PENDING)
                      .build()));

      assertThatThrownBy(
              () -> sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[DR-05] Phase B 보상에서 intent 취소가 불가하면 pending 상태를 즉시 롤백하지 않는다")
    void phase_b_보상_취소_불가_시_즉시_롤백하지_않음() {
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(refundAvailableReservation()))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(
                    current.toBuilder()
                        .status(ReservationStatus.DEADLINE_REFUND_AVAILABLE)
                        .build());
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
      given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(false);

      assertThatThrownBy(
              () -> sut.execute(new ClaimExpiredRefundReservationCommand(RESERVATION_ID, BUYER_ID)))
          .isInstanceOf(BusinessException.class);

      assertThat(latestSaved.get().getStatus())
          .isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
    }
  }

  private Reservation refundAvailableReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(BUYER_ID)
        .trainerId(TRAINER_ID)
        .slotId(1L)
        .reservationDate(LocalDate.of(2025, 5, 30))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.DEADLINE_REFUND_AVAILABLE)
        .orderId(ORDER_ID)
        .bookedPriceAmount(50_000)
        .version(0L)
        .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
        .contractDeadlineEpochSeconds(FIXED_NOW.minusSeconds(60).getEpochSecond())
        .build();
  }

  private Reservation pendingReservation(LocalDateTime contractDeadlineAt) {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(BUYER_ID)
        .trainerId(TRAINER_ID)
        .slotId(1L)
        .reservationDate(LocalDate.of(2025, 5, 30))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .orderId(ORDER_ID)
        .bookedPriceAmount(50_000)
        .version(0L)
        .contractDeadlineAt(contractDeadlineAt)
        .contractDeadlineEpochSeconds(contractDeadlineAt.atZone(ZONE).toEpochSecond())
        .build();
  }

  private ReservationExecutionWriteView web3() {
    return web3("MARKETPLACE_CLASS_EXPIRED_REFUND", "AWAITING_SIGNATURE", "intent-1");
  }

  private ReservationExecutionWriteView web3(
      String actionType, String intentStatus, String intentId) {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
        actionType,
        "0x" + "0".repeat(63) + "1",
        new ReservationExecutionWriteView.ExecutionIntent(
            intentId, intentStatus, LocalDateTime.now(FIXED_CLOCK).plusMinutes(5), 300L),
        new ReservationExecutionWriteView.Execution("EIP7702", 1),
        null,
        null,
        false,
        null,
        null);
  }

  private ReservationExecutionStateView state(
      String actionType, String intentStatus, String intentId, Long requesterUserId) {
    return new ReservationExecutionStateView(intentId, intentStatus, actionType, requesterUserId);
  }
}
