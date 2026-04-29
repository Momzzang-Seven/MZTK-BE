package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent.EscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CreateReservationService 단위 테스트")
class CreateReservationServiceTest {

  @Mock private GetClassSlotInfoUseCase getClassSlotInfoUseCase;
  @Mock private GetClassInfoUseCase getClassInfoUseCase;
  @Mock private CheckTrainerSanctionPort checkTrainerSanctionPort;
  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private ApplicationEventPublisher eventPublisher;

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
  private static final int CAPACITY = 3;

  private ClassSlot slot;
  private MarketplaceClass cls;
  private CreateReservationCommand command;

  @BeforeEach
  void setUp() {
    sut =
        new CreateReservationService(
            getClassSlotInfoUseCase,
            getClassInfoUseCase,
            checkTrainerSanctionPort,
            loadReservationPort,
            saveReservationPort,
            eventPublisher,
            FIXED_CLOCK);

    slot =
        ClassSlot.builder()
            .id(SLOT_ID)
            .classId(CLASS_ID)
            .daysOfWeek(List.of(DayOfWeek.MONDAY))
            .startTime(START_TIME)
            .capacity(CAPACITY)
            .active(true)
            .build();

    cls =
        MarketplaceClass.builder()
            .id(CLASS_ID)
            .trainerId(TRAINER_ID)
            .priceAmount(PRICE)
            .durationMinutes(60)
            .title("테스트 클래스")
            .active(true)
            .build();

    command =
        new CreateReservationCommand(
            USER_ID,
            CLASS_ID,
            SLOT_ID,
            MONDAY,
            START_TIME,
            null,
            BigInteger.valueOf(PRICE),
            "0x" + "a".repeat(130),
            "0x" + "b".repeat(130));
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[CR-01] 정상 예약 생성 시 PENDING 상태 반환 및 EscrowDispatchEvent(PURCHASE) 발행")
    void 정상_예약_생성() {
      // given
      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(getClassInfoUseCase.findById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(SLOT_ID, MONDAY))
          .willReturn(0);
      Reservation saved =
          Reservation.createPending(
              USER_ID,
              TRAINER_ID,
              SLOT_ID,
              MONDAY,
              START_TIME,
              60,
              null,
              "order-1",
              "ESCROW_DISPATCH_PENDING");
      given(saveReservationPort.save(any())).willReturn(saved);

      // when
      CreateReservationResult result = sut.execute(command);

      // then: PENDING 상태 반환
      assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
      then(saveReservationPort).should().save(any());

      // then: EscrowDispatchEvent(PURCHASE)가 발행되어야 함 (on-chain 호출은 AFTER_COMMIT 리스너가 담당)
      ArgumentCaptor<EscrowDispatchEvent> captor =
          ArgumentCaptor.forClass(EscrowDispatchEvent.class);
      then(eventPublisher).should().publishEvent(captor.capture());
      EscrowDispatchEvent event = captor.getValue();
      assertThat(event.action()).isEqualTo(EscrowAction.PURCHASE);
      assertThat(event.delegationSignature()).isEqualTo("0x" + "a".repeat(130));
      assertThat(event.executionSignature()).isEqualTo("0x" + "b".repeat(130));
      assertThat(event.amount()).isEqualTo(BigInteger.valueOf(PRICE));
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[CR-02] 슬롯 정원 초과 시 MARKETPLACE_RESERVATION_SLOT_FULL 예외")
    void 슬롯_정원_초과() {
      // given
      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(getClassInfoUseCase.findById(CLASS_ID)).willReturn(Optional.of(cls));
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
              BigInteger.valueOf(99_999L),
              "0x" + "a".repeat(130),
              "0x" + "b".repeat(130));

      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(getClassInfoUseCase.findById(CLASS_ID)).willReturn(Optional.of(cls));
      given(checkTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> sut.execute(wrongAmountCmd))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("[CR-04] 트레이너 정지 상태 시 MARKETPLACE_TRAINER_SUSPENDED 예외")
    void 트레이너_정지_상태() {
      // given
      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));
      given(getClassInfoUseCase.findById(CLASS_ID)).willReturn(Optional.of(cls));
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
              USER_ID,
              CLASS_ID,
              SLOT_ID,
              tuesday,
              START_TIME,
              null,
              BigInteger.valueOf(PRICE),
              "0x" + "a".repeat(130),
              "0x" + "b".repeat(130));

      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID)).willReturn(Optional.of(slot));

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
      ClassSlot inactiveSlot =
          ClassSlot.builder()
              .id(SLOT_ID)
              .classId(CLASS_ID)
              .daysOfWeek(List.of(DayOfWeek.MONDAY))
              .startTime(START_TIME)
              .capacity(CAPACITY)
              .active(false) // 비활성화
              .build();

      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID))
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

      ClassSlot mondaySlot =
          ClassSlot.builder()
              .id(SLOT_ID)
              .classId(CLASS_ID)
              .daysOfWeek(List.of(DayOfWeek.MONDAY))
              .startTime(pastTime)
              .capacity(CAPACITY)
              .active(true)
              .build();

      CreateReservationCommand pastCmd =
          new CreateReservationCommand(
              USER_ID,
              CLASS_ID,
              SLOT_ID,
              today,
              pastTime,
              null,
              BigInteger.valueOf(PRICE),
              "0x" + "a".repeat(130),
              "0x" + "b".repeat(130));

      given(getClassSlotInfoUseCase.findByIdWithLock(SLOT_ID)).willReturn(Optional.of(mondaySlot));

      // when & then
      assertThatThrownBy(() -> sut.execute(pastCmd))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_PAST_TIME.getCode()));
    }
  }
}
