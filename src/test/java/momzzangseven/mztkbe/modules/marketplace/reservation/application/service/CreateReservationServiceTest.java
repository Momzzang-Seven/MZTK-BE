package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.RetryableWeb3PreparationException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCandidateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort.ReservationClassSlotView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort.ReservationClassView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CreateReservationService 단위 테스트")
class CreateReservationServiceTest {

  @Mock private LoadReservationClassPort loadReservationClassPort;
  @Mock private CheckTrainerSanctionPort checkTrainerSanctionPort;
  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  @Mock private SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private SaveReservationEscrowPort saveReservationEscrowPort;
  @Mock private SaveReservationActionStatePort saveReservationActionStatePort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private BindReservationActionStatePort bindReservationActionStatePort;
  @Mock private PrecheckReservationPurchasePort precheckReservationPurchasePort;
  @Mock private PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  @Mock private CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  @Mock private LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;
  @Mock private ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  @Mock private LoadReservationWalletPort loadReservationWalletPort;
  @Mock private LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;

  /**
   * Fixed clock: 2024-06-03T10:00:00 KST (UTC+9). MONDAY is set to 2024-06-10, which is after this
   * clock's date — always in the future.
   */
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2024-06-03T01:00:00Z"), // 10:00 KST
          java.time.ZoneId.of("Asia/Seoul"));

  private CreateReservationService sut;

  private static final Long USER_ID = 1L;
  private static final Long CLASS_ID = 10L;
  private static final Long TRAINER_ID = 100L;
  private static final Long SLOT_ID = 200L;

  // 다음 주 월요일
  private static final LocalDate MONDAY =
      LocalDate.of(2024, 6, 3).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
  private static final LocalTime START_TIME = LocalTime.of(10, 0);
  private static final int PRICE = 50_000;
  private static final BigInteger PRICE_BASE_UNITS = new BigInteger("50000000000000000000000");
  private static final int CAPACITY = 3;

  private ReservationClassSlotView slot;
  private ReservationClassView cls;
  private CreateReservationCommand command;

  @BeforeEach
  void setUp() {
    sut =
        new CreateReservationService(
            loadReservationClassPort,
            checkTrainerSanctionPort,
            loadReservationPort,
            saveReservationPort,
            loadReservationCreateIdempotencyPort,
            saveReservationCreateIdempotencyPort,
            loadReservationEscrowPort,
            saveReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            precheckReservationPurchasePort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationExecutionWritePort,
            loadReservationExecutionStatePort,
            loadReservationExecutionCandidatePort,
            replayConfirmedReservationExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            FIXED_CLOCK);
    sut.setTransactionPort(ReservationTestTransactionPort.direct());

    slot = slotView(START_TIME, true);
    cls = classView(PRICE, 60, true);

    command =
        new CreateReservationCommand(
            USER_ID, CLASS_ID, SLOT_ID, MONDAY, START_TIME, null, "create-key", PRICE_BASE_UNITS);

    given(saveReservationCreateIdempotencyPort.reservePreparing(any(), any(), any(), any()))
        .willAnswer(
            invocation ->
                new SaveReservationCreateIdempotencyPort.ReserveCreateIdempotencyResult(
                    ReservationCreateIdempotency.preparing(
                        invocation.getArgument(0, Long.class),
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(2, String.class),
                        invocation.getArgument(3, LocalDateTime.class)),
                    true));
    given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
        .willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationIdAndActionType(any(), any()))
        .willReturn(Optional.empty());
    given(loadReservationExecutionCandidatePort.findByReservationResource(any()))
        .willReturn(List.of());
    given(loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(any(), any(), any(), any()))
        .willReturn(Optional.empty());
    given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
        .willReturn(0);
    given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
    given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
        .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
    given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
        .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(saveReservationEscrowPort.save(any()))
        .willAnswer(
            invocation ->
                invocation.getArgument(0, MarketplaceReservationEscrow.class).toBuilder()
                    .id(10L)
                    .build());
    given(saveReservationActionStatePort.save(any()))
        .willAnswer(
            invocation ->
                invocation.getArgument(0, MarketplaceReservationActionState.class).toBuilder()
                    .id(20L)
                    .build());
    given(bindReservationActionStatePort.bindExecutionIntent(any(), any(), any()))
        .willAnswer(
            invocation ->
                Optional.of(
                    MarketplaceReservationActionState.builder()
                        .id(invocation.getArgument(0, Long.class))
                        .attemptToken(invocation.getArgument(1, String.class))
                        .executionIntentPublicId(invocation.getArgument(2, String.class))
                        .status(ReservationActionStateStatus.INTENT_BOUND)
                        .build()));
  }

  private ReservationClassSlotView slotView(LocalTime startTime, boolean active) {
    return new ReservationClassSlotView(
        SLOT_ID, CLASS_ID, List.of(DayOfWeek.MONDAY), startTime, CAPACITY, active);
  }

  private ReservationClassView classView(int priceAmount, int durationMinutes, boolean active) {
    return new ReservationClassView(
        CLASS_ID, TRAINER_ID, priceAmount, durationMinutes, "테스트 클래스", active);
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[CR-01] 정상 예약 생성 시 PENDING 상태 반환 및 EscrowDispatchEvent(PURCHASE) 발행")
    void 정상_예약_생성() {
      // given
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
          .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      // when
      CreateReservationResult result = sut.execute(command);

      // then: purchase intent is bound and returned
      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
      assertThat(result.businessStatus()).isNull();
      assertThat(result.web3()).isNotNull();
      then(saveReservationPort).should(org.mockito.Mockito.times(2)).save(any());
      then(prepareReservationEscrowExecutionPort).should().preparePurchase(any());
      org.mockito.ArgumentCaptor<ReservationCreateIdempotency> idempotencyCaptor =
          org.mockito.ArgumentCaptor.forClass(ReservationCreateIdempotency.class);
      then(saveReservationCreateIdempotencyPort)
          .should(org.mockito.Mockito.times(2))
          .save(idempotencyCaptor.capture());
      assertThat(idempotencyCaptor.getAllValues())
          .extracting(ReservationCreateIdempotency::getStatus)
          .containsExactly(
              ReservationCreateIdempotencyStatus.BOUND,
              ReservationCreateIdempotencyStatus.COMPLETED);
    }

    @Test
    @DisplayName("[CR-09] Phase B 바인딩 실패 시 signable intent를 취소하고 hold를 실패 처리한다")
    void phase_b_바인딩_실패_보상() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
          .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      AtomicInteger lockedLoads = new AtomicInteger();
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                if (lockedLoads.getAndIncrement() == 0) {
                  return Optional.of(current.toBuilder().version(99L).build());
                }
                return Optional.of(current);
              });
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(true);

      assertThatThrownBy(() -> sut.execute(command))
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
      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("[CR-11] Phase B 보상에서 intent 취소가 불가하면 purchase hold를 즉시 실패 처리하지 않는다")
    void phase_b_보상_취소_불가_시_즉시_실패처리하지_않음() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
          .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(
              invocation -> {
                Reservation current = latestSaved.get();
                return Optional.of(current.toBuilder().version(99L).build());
              });
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(false);

      assertThatThrownBy(() -> sut.execute(command)).isInstanceOf(BusinessException.class);

      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.HOLDING);
    }

    @Test
    @DisplayName("[CR-14] Phase B 전에 purchase hold가 만료되면 intent를 취소하고 HOLD_EXPIRED로 정리한다")
    void phase_b_전에_hold가_만료되면_intent를_취소하고_hold_expired로_정리한다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(
              invocation ->
                  Optional.of(
                      latestSaved.get().toBuilder()
                          .holdExpiresAt(LocalDateTime.now(FIXED_CLOCK).minusSeconds(1))
                          .build()));
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(true);

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_STALE_SIGN_REQUEST.getCode()));

      then(cancelReservationEscrowExecutionPort)
          .should()
          .cancelSignableIntent(
              org.mockito.ArgumentMatchers.eq("intent-1"),
              org.mockito.ArgumentMatchers.eq("MARKETPLACE_PHASE_B_BIND_FAILED"),
              any());
      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.HOLD_EXPIRED);
    }

    @Test
    @DisplayName("[CR-08] 같은 슬롯의 만료된 unbound hold는 HOLD_EXPIRED로 전환하고 새 예약을 진행한다")
    void 만료된_홀드_정리_후_예약_생성() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.of(expiredUnboundHold()));
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
          .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      CreateReservationResult result = sut.execute(command);

      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
      assertThat(result.businessStatus()).isNull();
      then(saveReservationPort).should(org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    @DisplayName("[CR-08B] 만료된 hold의 AWAITING_SIGNATURE intent를 취소한 뒤 HOLD_EXPIRED로 해제한다")
    void 만료된_hold의_signable_intent를_취소하고_예약_생성() {
      Reservation expiredHold = expiredUnboundHold();
      MarketplaceReservationActionState actionState =
          activePurchaseActionState(expiredHold).toBuilder()
              .executionIntentPublicId("intent-old")
              .build();
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.of(expiredHold));
      given(
              loadReservationActionStatePort.findLatestByReservationIdAndActionType(
                  expiredHold.getId(), ReservationEscrowAction.PURCHASE))
          .willReturn(Optional.of(actionState));
      given(loadReservationExecutionStatePort.loadState("intent-old"))
          .willReturn(
              new ReservationExecutionStateView(
                  "intent-old", "AWAITING_SIGNATURE", "MARKETPLACE_CLASS_PURCHASE", USER_ID),
              new ReservationExecutionStateView(
                  "intent-old", "AWAITING_SIGNATURE", "MARKETPLACE_CLASS_PURCHASE", USER_ID),
              new ReservationExecutionStateView(
                  "intent-old", "CANCELED", "MARKETPLACE_CLASS_PURCHASE", USER_ID));
      given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
          .willReturn(true);
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
          .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation reservation = invocation.getArgument(0, Reservation.class);
                Reservation saved =
                    reservation.getId() == null
                        ? reservation.toBuilder().id(1L).version(0L).build()
                        : reservation;
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      CreateReservationResult result = sut.execute(command);

      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
      then(cancelReservationEscrowExecutionPort)
          .should()
          .cancelSignableIntent(
              org.mockito.ArgumentMatchers.eq("intent-old"),
              org.mockito.ArgumentMatchers.eq("MARKETPLACE_EXPIRED_HOLD_RELEASE"),
              any());
      org.mockito.ArgumentCaptor<Reservation> reservationCaptor =
          org.mockito.ArgumentCaptor.forClass(Reservation.class);
      then(saveReservationPort)
          .should(org.mockito.Mockito.atLeastOnce())
          .save(reservationCaptor.capture());
      assertThat(reservationCaptor.getAllValues())
          .extracting(Reservation::getStatus)
          .contains(ReservationStatus.HOLD_EXPIRED, ReservationStatus.HOLDING);
    }

    @Test
    @DisplayName(
        "[CR-16] retryable purchase prepare 실패는 hold를 유지하고 action-state만 retryable failed로 둔다")
    void retryable_prepare_실패는_hold를_유지한다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty());
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willThrow(new RetryableWeb3PreparationException("transient prepare failure"));

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(RetryableWeb3PreparationException.class);

      assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.HOLDING);
      org.mockito.ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
          org.mockito.ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
      then(saveReservationActionStatePort)
          .should(org.mockito.Mockito.times(2))
          .save(actionCaptor.capture());
      MarketplaceReservationActionState failedAction = actionCaptor.getAllValues().get(1);
      assertThat(failedAction.getStatus())
          .isEqualTo(ReservationActionStateStatus.PREPARATION_FAILED);
      assertThat(failedAction.getRetryable()).isTrue();
      org.mockito.ArgumentCaptor<ReservationCreateIdempotency> idempotencyCaptor =
          org.mockito.ArgumentCaptor.forClass(ReservationCreateIdempotency.class);
      then(saveReservationCreateIdempotencyPort)
          .should(org.mockito.Mockito.times(2))
          .save(idempotencyCaptor.capture());
      assertThat(idempotencyCaptor.getAllValues())
          .extracting(ReservationCreateIdempotency::getStatus)
          .containsExactly(
              ReservationCreateIdempotencyStatus.BOUND, ReservationCreateIdempotencyStatus.BOUND);
    }

    @Test
    @DisplayName("[CR-17] 같은 create idempotency key retry는 새 purchase action-state로 CAS 교체한다")
    void retryable_purchase_action은_같은_create_key로_새_attempt를_준비한다() {
      Reservation existingReservation =
          replayReservation(ReservationStatus.HOLDING, null).toBuilder()
              .id(33L)
              .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
              .pendingAttemptToken("attempt-old")
              .holdExpiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(5))
              .buyerWalletAddress("0x1111111111111111111111111111111111111111")
              .trainerWalletAddress("0x2222222222222222222222222222222222222222")
              .tokenAddress("0x3333333333333333333333333333333333333333")
              .priceBaseUnits(PRICE_BASE_UNITS.toString())
              .expectedContractDeadlineEpochSeconds(
                  FIXED_CLOCK.instant().plusSeconds(2_592_000L).getEpochSecond())
              .expectedContractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).plusDays(30))
              .version(7L)
              .build();
      ReservationCreateIdempotency idempotency =
          ReservationCreateIdempotency.builder()
              .id(501L)
              .buyerId(USER_ID)
              .keyHash("key-hash")
              .payloadHash(expectedPayloadHash())
              .status(ReservationCreateIdempotencyStatus.BOUND)
              .reservationId(existingReservation.getId())
              .escrowId(10L)
              .actionStateId(20L)
              .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
              .build();
      MarketplaceReservationActionState retryableAction =
          MarketplaceReservationActionState.builder()
              .id(20L)
              .reservationId(existingReservation.getId())
              .escrowId(10L)
              .actionType(ReservationEscrowAction.PURCHASE)
              .actorType(ReservationEscrowActorType.BUYER)
              .actorUserId(USER_ID)
              .attemptNo(1)
              .attemptToken("attempt-old")
              .status(ReservationActionStateStatus.PREPARATION_FAILED)
              .rootIdempotencyKey("buyer:" + USER_ID + ":create:key-hash")
              .expectedEscrowStatus(ReservationEscrowStatus.NONE)
              .priorEscrowStatus(ReservationEscrowStatus.NONE)
              .retryable(true)
              .build();
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.of(idempotency));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>(existingReservation);
      given(loadReservationPort.findByIdWithLock(existingReservation.getId()))
          .willAnswer(invocation -> Optional.of(latestSaved.get()));
      given(
              loadReservationActionStatePort.findLatestByReservationIdAndActionTypeWithLock(
                  existingReservation.getId(), ReservationEscrowAction.PURCHASE))
          .willReturn(Optional.of(retryableAction));
      given(
              loadReservationExecutionCandidatePort.findByReservationResource(
                  existingReservation.getId(), existingReservation.getOrderKey()))
          .willReturn(List.of());
      given(loadReservationEscrowPort.findByReservationIdWithLock(existingReservation.getId()))
          .willReturn(
              Optional.of(
                  MarketplaceReservationEscrow.builder()
                      .id(10L)
                      .reservationId(existingReservation.getId())
                      .escrowStatus(ReservationEscrowStatus.NONE)
                      .holdExpiresAt(existingReservation.getHoldExpiresAt())
                      .build()));
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved = invocation.getArgument(0, Reservation.class);
                latestSaved.set(saved);
                return saved;
              });
      org.mockito.BDDMockito.willAnswer(
              invocation -> invocation.getArgument(0, MarketplaceReservationEscrow.class))
          .given(saveReservationEscrowPort)
          .save(any());
      org.mockito.BDDMockito.willAnswer(
              invocation -> {
                MarketplaceReservationActionState action =
                    invocation.getArgument(0, MarketplaceReservationActionState.class);
                if (action.getId() == null) {
                  return action.toBuilder().id(21L).build();
                }
                return action;
              })
          .given(saveReservationActionStatePort)
          .save(any());
      given(saveReservationCreateIdempotencyPort.replaceActionStateIfCurrent(501L, 20L, 21L))
          .willReturn(Optional.of(idempotency.replaceActionState(21L)));
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      CreateReservationResult result = sut.execute(command);

      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
      org.mockito.ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
          org.mockito.ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
      then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
      assertThat(escrowCaptor.getValue().getHoldExpiresAt())
          .isAfter(existingReservation.getHoldExpiresAt());
      then(saveReservationCreateIdempotencyPort)
          .should()
          .replaceActionStateIfCurrent(501L, 20L, 21L);
      org.mockito.ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
          org.mockito.ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
      then(saveReservationActionStatePort)
          .should(org.mockito.Mockito.times(2))
          .save(actionCaptor.capture());
      assertThat(actionCaptor.getAllValues())
          .extracting(MarketplaceReservationActionState::getStatus)
          .containsExactly(
              ReservationActionStateStatus.STALE, ReservationActionStateStatus.PREPARING);
      assertThat(actionCaptor.getAllValues().get(1).getAttemptNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("[CR-15] 만료된 hold라도 matching shared execution 후보가 active면 새 예약을 막는다")
    void 만료된_hold에_active_candidate가_있으면_해제하지_않는다() {
      Reservation expiredHold = expiredUnboundHold();
      MarketplaceReservationActionState actionState = activePurchaseActionState(expiredHold);
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.of(expiredHold));
      given(
              loadReservationActionStatePort.findLatestByReservationIdAndActionType(
                  expiredHold.getId(), ReservationEscrowAction.PURCHASE))
          .willReturn(Optional.of(actionState));
      given(
              loadReservationExecutionCandidatePort.findByReservationResource(
                  expiredHold.getId(), expiredHold.getOrderKey()))
          .willReturn(List.of(activePurchaseCandidate(expiredHold, actionState)));

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-10] 완료된 create idempotency replay는 저장 스냅샷 대신 현재 execution intent를 재조회한다")
    void 완료된_idempotency_replay는_execution_intent를_재조회한다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      Reservation existingReservation =
          replayReservation(ReservationStatus.PURCHASE_PENDING, "intent-existing");
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.COMPLETED)
                      .reservationId(existingReservation.getId())
                      .responseSnapshotJson("{\"stale\":true}")
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      given(loadReservationExecutionWritePort.load(USER_ID, "intent-existing")).willReturn(web3());

      CreateReservationResult result = sut.execute(command);

      assertThat(result.reservationId()).isEqualTo(existingReservation.getId());
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().existing()).isTrue();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "[CR-10A] 완료된 create idempotency replay에서 confirmed intent는 즉시 replay 후 최신 예약을 반환한다")
    void 완료된_idempotency_replay는_confirmed_intent를_즉시_replay한다() {
      Reservation existingReservation =
          replayReservation(ReservationStatus.PURCHASE_PENDING, "intent-existing");
      Reservation repairedReservation =
          existingReservation.toBuilder()
              .status(ReservationStatus.PENDING)
              .currentExecutionIntentPublicId(null)
              .build();
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.COMPLETED)
                      .reservationId(existingReservation.getId())
                      .responseSnapshotJson("{\"stale\":true}")
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation), Optional.of(repairedReservation));
      given(loadReservationExecutionStatePort.loadState("intent-existing"))
          .willReturn(
              new ReservationExecutionStateView(
                  "intent-existing", "CONFIRMED", "MARKETPLACE_CLASS_PURCHASE", USER_ID));
      given(
              replayConfirmedReservationExecutionPort.replayConfirmed(
                  "intent-existing", "MARKETPLACE_CLASS_PURCHASE"))
          .willReturn(true);

      CreateReservationResult result = sut.execute(command);

      assertThat(result.reservationId()).isEqualTo(existingReservation.getId());
      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.PENDING);
      assertThat(result.businessStatus()).isEqualTo(ReservationStatus.PENDING);
      assertThat(result.web3()).isNull();
      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
      then(replayConfirmedReservationExecutionPort)
          .should()
          .replayConfirmed("intent-existing", "MARKETPLACE_CLASS_PURCHASE");
    }

    @Test
    @DisplayName("[CR-10B] 완료된 create idempotency replay는 외부 funding precheck 전에 반환한다")
    void 완료된_idempotency_replay는_external_precheck를_건너뛴다() {
      Reservation existingReservation =
          replayReservation(ReservationStatus.PURCHASE_PENDING, "intent-existing");
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.COMPLETED)
                      .reservationId(existingReservation.getId())
                      .responseSnapshotJson("{\"stale\":true}")
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      given(loadReservationExecutionWritePort.load(USER_ID, "intent-existing")).willReturn(web3());
      org.mockito.BDDMockito.willThrow(
              new BusinessException(
                  ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE,
                  "external precheck must not run for completed replay"))
          .given(precheckReservationPurchasePort)
          .precheckPurchase(any());

      CreateReservationResult result = sut.execute(command);

      assertThat(result.reservationId()).isEqualTo(existingReservation.getId());
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().existing()).isTrue();
      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-10C] precheck 실패 직전에 같은 idempotency winner가 생기면 실패 대신 replay한다")
    void precheck_실패_후_concurrent_winner가_있으면_replay한다() {
      Reservation existingReservation =
          replayReservation(ReservationStatus.PURCHASE_PENDING, "intent-winner");
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty())
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.BOUND)
                      .reservationId(existingReservation.getId())
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      given(loadReservationExecutionWritePort.load(USER_ID, "intent-winner")).willReturn(web3());
      org.mockito.BDDMockito.willThrow(
              new BusinessException(
                  ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE, "precheck raced with winner"))
          .given(precheckReservationPurchasePort)
          .precheckPurchase(any());

      CreateReservationResult result = sut.execute(command);

      assertThat(result.reservationId()).isEqualTo(existingReservation.getId());
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().existing()).isTrue();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-12] BOUND create idempotency replay도 저장 스냅샷 대신 현재 execution intent를 재조회한다")
    void bound_idempotency_replay도_execution_intent를_재조회한다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      Reservation existingReservation =
          replayReservation(ReservationStatus.PURCHASE_PENDING, "intent-bound");
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.BOUND)
                      .reservationId(existingReservation.getId())
                      .responseSnapshotJson("{\"stale\":true}")
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      given(loadReservationExecutionWritePort.load(USER_ID, "intent-bound")).willReturn(web3());

      CreateReservationResult result = sut.execute(command);

      assertThat(result.reservationId()).isEqualTo(existingReservation.getId());
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().existing()).isTrue();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "[CR-12C] BOUND idempotency가 unbound PREPARING action이면 null web3 replay 대신 conflict")
    void bound_idempotency_unbound_preparing_action은_conflict() {
      Reservation existingReservation =
          replayReservation(ReservationStatus.HOLDING, null).toBuilder()
              .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
              .pendingAttemptToken("attempt-active")
              .holdExpiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(5))
              .build();
      MarketplaceReservationActionState preparingAction =
          MarketplaceReservationActionState.builder()
              .id(20L)
              .reservationId(existingReservation.getId())
              .escrowId(10L)
              .actionType(ReservationEscrowAction.PURCHASE)
              .actorType(ReservationEscrowActorType.BUYER)
              .actorUserId(USER_ID)
              .attemptNo(1)
              .attemptToken("attempt-active")
              .status(ReservationActionStateStatus.PREPARING)
              .build();
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.BOUND)
                      .reservationId(existingReservation.getId())
                      .escrowId(10L)
                      .actionStateId(20L)
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(loadReservationPort.findById(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      given(loadReservationPort.findByIdWithLock(existingReservation.getId()))
          .willReturn(Optional.of(existingReservation));
      given(
              loadReservationActionStatePort.findLatestByReservationIdAndActionTypeWithLock(
                  existingReservation.getId(), ReservationEscrowAction.PURCHASE))
          .willReturn(Optional.of(preparingAction));
      given(loadReservationActionStatePort.findById(20L)).willReturn(Optional.of(preparingAction));

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-12B] 같은 idempotency key가 다른 payload로 재사용되면 conflict를 반환한다")
    void 같은_idempotency_key_다른_payload_재사용은_conflict() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash("0x" + "f".repeat(64))
                      .status(ReservationCreateIdempotencyStatus.PREPARING)
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-11] 같은 idempotency key 동시 생성 loser는 winner row를 재조회해 deterministic replay한다")
    void 동시_생성_loser는_winner를_재조회한다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(Optional.empty())
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.BOUND)
                      .reservationId(44L)
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      ReservationCreateIdempotency winnerKey =
          ReservationCreateIdempotency.builder()
              .buyerId(USER_ID)
              .payloadHash(expectedPayloadHash())
              .status(ReservationCreateIdempotencyStatus.BOUND)
              .reservationId(44L)
              .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
              .build();
      given(saveReservationCreateIdempotencyPort.reservePreparing(any(), any(), any(), any()))
          .willReturn(
              new SaveReservationCreateIdempotencyPort.ReserveCreateIdempotencyResult(
                  winnerKey, false));
      Reservation existingReservation =
          replayReservation(ReservationStatus.PURCHASE_PENDING, "intent-winner").toBuilder()
              .id(44L)
              .build();
      given(loadReservationPort.findById(44L)).willReturn(Optional.of(existingReservation));
      given(loadReservationExecutionWritePort.load(USER_ID, "intent-winner")).willReturn(web3());

      CreateReservationResult result = sut.execute(command);

      assertThat(result.reservationId()).isEqualTo(44L);
      assertThat(result.web3()).isNotNull();
      assertThat(result.web3().existing()).isTrue();
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-13] FAILED create idempotency는 새 hold와 execution intent 생성으로 재시작한다")
    void failed_idempotency는_새_예약_생성으로_재시작한다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .keyHash("key")
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.FAILED)
                      .responseSnapshotJson("{\"status\":\"FAILED\"}")
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));
      given(
              loadReservationPort.findActiveByBuyerAndSlotDateTimeWithLock(
                  any(), any(), any(), any()))
          .willReturn(Optional.empty());
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      given(loadReservationWalletPort.loadActiveWalletAddress(USER_ID))
          .willReturn(Optional.of("0x1111111111111111111111111111111111111111"));
      given(loadReservationWalletPort.loadActiveWalletAddress(TRAINER_ID))
          .willReturn(Optional.of("0x2222222222222222222222222222222222222222"));
      given(loadReservationEscrowPaymentConfigPort.load())
          .willReturn(
              new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                  "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
      AtomicReference<Reservation> latestSaved = new AtomicReference<>();
      given(saveReservationPort.save(any()))
          .willAnswer(
              invocation -> {
                Reservation saved =
                    invocation.getArgument(0, Reservation.class).toBuilder()
                        .id(1L)
                        .version(0L)
                        .build();
                latestSaved.set(saved);
                return saved;
              });
      given(loadReservationPort.findByIdWithLock(1L))
          .willAnswer(invocation -> Optional.ofNullable(latestSaved.get()));
      given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
          .willReturn(new PrepareReservationEscrowResult(web3()));

      CreateReservationResult result = sut.execute(command);

      assertThat(result.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
      assertThat(result.businessStatus()).isNull();
      org.mockito.ArgumentCaptor<ReservationCreateIdempotency> idempotencyCaptor =
          org.mockito.ArgumentCaptor.forClass(ReservationCreateIdempotency.class);
      then(saveReservationCreateIdempotencyPort)
          .should(org.mockito.Mockito.times(3))
          .save(idempotencyCaptor.capture());
      assertThat(idempotencyCaptor.getAllValues())
          .extracting(ReservationCreateIdempotency::getStatus)
          .containsExactly(
              ReservationCreateIdempotencyStatus.PREPARING,
              ReservationCreateIdempotencyStatus.BOUND,
              ReservationCreateIdempotencyStatus.COMPLETED);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[CR-02] 슬롯 정원 초과 시 MARKETPLACE_RESERVATION_SLOT_FULL 예외")
    void 슬롯_정원_초과() {
      // given
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(CAPACITY); // 이미 가득 참

      // when & then
      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_SLOT_FULL.getCode()));
    }

    @Test
    @DisplayName("[CR-03] 서명 금액 불일치 시 MARKETPLACE_RESERVATION_PRICE_MISMATCH 예외")
    void 서명_금액_불일치() {
      // given: 서명 금액이 실제 수업 가격과 다름
      CreateReservationCommand wrongAmountCmd =
          new CreateReservationCommand(
              USER_ID,
              CLASS_ID,
              SLOT_ID,
              MONDAY,
              START_TIME,
              null,
              "wrong-amount",
              BigInteger.valueOf(99_999L));

      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      org.mockito.BDDMockito.willThrow(
              new BusinessException(
                  ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH,
                  "signed amount does not match marketplace class price token base units"))
          .given(precheckReservationPurchasePort)
          .precheckPurchase(any());

      // when & then
      assertThatThrownBy(() -> sut.execute(wrongAmountCmd))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("[CR-03A] create idempotency key가 없으면 외부 precheck 전에 거절한다")
    void create_idempotency_key가_없으면_거절() {
      CreateReservationCommand missingKey =
          new CreateReservationCommand(
              USER_ID, CLASS_ID, SLOT_ID, MONDAY, START_TIME, null, " ", PRICE_BASE_UNITS);

      assertThatThrownBy(() -> sut.execute(missingKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("idempotencyKey");

      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
      then(saveReservationPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[CR-03B] 외부 구매 precheck 실패는 예약 hold 없이 FAILED idempotency row만 남긴다")
    void 외부_precheck_실패는_precheck_failed_idempotency를_남긴다() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(saveReservationCreateIdempotencyPort.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0, ReservationCreateIdempotency.class));
      org.mockito.BDDMockito.willThrow(
              new BusinessException(
                  ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE,
                  "buyer balance is insufficient"))
          .given(precheckReservationPurchasePort)
          .precheckPurchase(any());

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE.getCode()));

      then(saveReservationPort).shouldHaveNoInteractions();
      org.mockito.ArgumentCaptor<ReservationCreateIdempotency> idempotencyCaptor =
          org.mockito.ArgumentCaptor.forClass(ReservationCreateIdempotency.class);
      then(saveReservationCreateIdempotencyPort).should().save(idempotencyCaptor.capture());
      assertThat(idempotencyCaptor.getValue().getStatus())
          .isEqualTo(ReservationCreateIdempotencyStatus.FAILED);
      assertThat(idempotencyCaptor.getValue().getResponseSnapshotJson())
          .contains("PRECHECK_FAILED")
          .contains(ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE.getCode());
    }

    @Test
    @DisplayName("[CR-04] 트레이너 정지 상태 시 MARKETPLACE_TRAINER_SUSPENDED 예외")
    void 트레이너_정지_상태() {
      // given
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(true);

      // when & then
      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_TRAINER_SUSPENDED.getCode()));
    }

    @Test
    @DisplayName("[CR-05] 요일 불일치 시 MARKETPLACE_RESERVATION_INVALID_SLOT_DATE 예외")
    void 요일_불일치() {
      // given: 슬롯은 MONDAY만 허용하지만 화요일 날짜 요청
      LocalDate tuesday = MONDAY.plusDays(1);
      CreateReservationCommand wrongDayCmd =
          new CreateReservationCommand(
              USER_ID, CLASS_ID, SLOT_ID, tuesday, START_TIME, null, "wrong-day", PRICE_BASE_UNITS);

      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));

      // when & then
      assertThatThrownBy(() -> sut.execute(wrongDayCmd))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE.getCode()));
    }

    @Test
    @DisplayName("[CR-06] 비활성 슬롯 예약 시도 시 MARKETPLACE_RESERVATION_INVALID_SLOT_DATE 예외")
    void 비활성_슬롯() {
      // given: 슬롯이 soft-delete 된 상태
      ReservationClassSlotView inactiveSlot = slotView(START_TIME, false);

      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID))
          .willReturn(Optional.of(inactiveSlot));

      // when & then
      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE.getCode()));
    }

    @Test
    @DisplayName("[CR-07] 이미 지난 시간대 예약 시도 시 MARKETPLACE_RESERVATION_PAST_TIME 예외")
    void 과거_시간대_예약() {
      // given: FIXED_CLOCK = 2024-06-03 10:00 KST, 과거 날짜(2024-06-03 09:00)로 요청
      // session: 2024-06-03(월) 09:00 → clock보다 1시간 앞
      LocalDate today = LocalDate.of(2024, 6, 3); // 월요일
      LocalTime pastTime = LocalTime.of(9, 0);

      ReservationClassSlotView mondaySlot = slotView(pastTime, true);

      CreateReservationCommand pastCmd =
          new CreateReservationCommand(
              USER_ID, CLASS_ID, SLOT_ID, today, pastTime, null, "past-time", PRICE_BASE_UNITS);

      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID))
          .willReturn(Optional.of(mondaySlot));

      // when & then
      assertThatThrownBy(() -> sut.execute(pastCmd))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_PAST_TIME.getCode()));
    }

    @Test
    @DisplayName("[CR-12] PREPARING create idempotency가 살아 있으면 active execution conflict를 반환한다")
    void preparing_idempotency가_살아있으면_conflict() {
      given(loadReservationClassPort.findSlotByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(loadReservationClassPort.findClassById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(any(), any()))
          .willReturn(
              Optional.of(
                  ReservationCreateIdempotency.builder()
                      .buyerId(USER_ID)
                      .payloadHash(expectedPayloadHash())
                      .status(ReservationCreateIdempotencyStatus.PREPARING)
                      .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
                      .build()));

      assertThatThrownBy(() -> sut.execute(command))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));
      then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
      then(precheckReservationPurchasePort).shouldHaveNoInteractions();
    }
  }

  private ReservationExecutionWriteView web3() {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
        "MARKETPLACE_CLASS_PURCHASE",
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

  private Reservation expiredUnboundHold() {
    return Reservation.builder()
        .id(77L)
        .userId(USER_ID)
        .trainerId(TRAINER_ID)
        .slotId(SLOT_ID)
        .reservationDate(MONDAY)
        .reservationTime(START_TIME)
        .durationMinutes(60)
        .status(ReservationStatus.HOLDING)
        .orderId("123e4567-e89b-12d3-a456-426614174000")
        .orderKey("0x" + "0".repeat(32) + "123e4567e89b12d3a456426614174000")
        .bookedPriceAmount(PRICE)
        .holdExpiresAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
        .pendingAttemptToken("attempt-old")
        .version(0L)
        .build();
  }

  private MarketplaceReservationActionState activePurchaseActionState(Reservation reservation) {
    return MarketplaceReservationActionState.builder()
        .id(880L)
        .reservationId(reservation.getId())
        .escrowId(990L)
        .actionType(ReservationEscrowAction.PURCHASE)
        .actorType(ReservationEscrowActorType.BUYER)
        .actorUserId(reservation.getUserId())
        .attemptNo(1)
        .attemptToken(reservation.getPendingAttemptToken())
        .status(ReservationActionStateStatus.PREPARING)
        .build();
  }

  private ReservationExecutionCandidateView activePurchaseCandidate(
      Reservation reservation, MarketplaceReservationActionState actionState) {
    return new ReservationExecutionCandidateView(
        "intent-orphan",
        "SIGNED",
        "MARKETPLACE_CLASS_PURCHASE",
        reservation.getUserId(),
        1L,
        "PENDING",
        "0xabc",
        new ReservationExecutionCandidateView.PayloadEvidence(
            1,
            reservation.getId(),
            actionState.getEscrowId(),
            actionState.getId(),
            actionState.getAttemptToken(),
            reservation.getOrderKey(),
            "MARKETPLACE_CLASS_PURCHASE"),
        true);
  }

  private Reservation replayReservation(ReservationStatus status, String intentId) {
    return Reservation.builder()
        .id(33L)
        .userId(USER_ID)
        .trainerId(TRAINER_ID)
        .slotId(SLOT_ID)
        .reservationDate(MONDAY)
        .reservationTime(START_TIME)
        .durationMinutes(60)
        .status(status)
        .orderId("123e4567-e89b-12d3-a456-426614174000")
        .orderKey("0x" + "0".repeat(32) + "123e4567e89b12d3a456426614174000")
        .currentExecutionIntentPublicId(intentId)
        .bookedPriceAmount(PRICE)
        .version(0L)
        .build();
  }

  private String expectedPayloadHash() {
    return sha256Hex(
        String.join(
            ":",
            String.valueOf(USER_ID),
            String.valueOf(CLASS_ID),
            String.valueOf(SLOT_ID),
            MONDAY.toString(),
            START_TIME.toString(),
            PRICE_BASE_UNITS.toString(),
            String.valueOf(TRAINER_ID),
            String.valueOf(PRICE)));
  }

  private String sha256Hex(String value) {
    try {
      return "0x"
          + toHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String toHex(byte[] bytes) {
    char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xff;
      chars[i * 2] = Character.forDigit(value >>> 4, 16);
      chars[i * 2 + 1] = Character.forDigit(value & 0x0f, 16);
    }
    return new String(chars);
  }
}
