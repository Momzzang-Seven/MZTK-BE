package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetSlotReservationInfoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetClassReservationInfoService 단위 테스트")
class GetClassReservationInfoServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private LoadClassSlotPort loadClassSlotPort;
  @Mock private GetSlotReservationInfoUseCase getSlotReservationInfoUseCase;

  /** 2024-06-03 (월요일) 정오로 고정된 Clock — 달력 결과를 결정론적으로 검증 */
  @Spy
  private Clock clock = Clock.fixed(Instant.parse("2024-06-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  @InjectMocks private GetClassReservationInfoService sut;

  private static final Long CLASS_ID = 1L;
  private static final Long SLOT_ID = 10L;

  private MarketplaceClass activeClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(5L)
        .title("PT 클래스")
        .category(ClassCategory.PILATES)
        .description("설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  private ClassSlot mondaySlot(int capacity) {
    return ClassSlot.builder()
        .id(SLOT_ID)
        .classId(CLASS_ID)
        .daysOfWeek(List.of(DayOfWeek.MONDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(capacity)
        .active(true)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[GRI-01] 활성 슬롯 없는 클래스 조회 시 availableDates 빈 리스트 반환")
    void 활성슬롯_없음() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(activeClass()));
      given(loadClassSlotPort.findByClassId(CLASS_ID)).willReturn(List.of());

      // when
      GetClassReservationInfoResult result =
          sut.execute(new GetClassReservationInfoQuery(CLASS_ID));

      // then
      assertThat(result.availableDates()).isEmpty();
    }

    @Test
    @DisplayName("[GRI-02] 월요일 슬롯 조회 시 28일 창 내 미래 월요일 날짜에 해당 슬롯만 포함")
    void 월요일_슬롯_4주_조회() {
      // given: 2024-06-03(월) 12:00 KST 기준.
      // slot startTime=10:00 이므로 오늘(6/3) 10:00는 이미 지남 → 필터 제외.
      // 28일 창 내 남은 월요일: 6/10, 6/17, 6/24 → 3개
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(activeClass()));
      given(loadClassSlotPort.findByClassId(CLASS_ID)).willReturn(List.of(mondaySlot(5)));
      java.time.LocalDate nextMonday = java.time.LocalDate.of(2024, 6, 10);
      given(
              getSlotReservationInfoUseCase.countActiveReservationsForDateRange(
                  org.mockito.ArgumentMatchers.eq(SLOT_ID),
                  org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.any()))
          .willReturn(java.util.Map.of(nextMonday, 2));

      // when
      GetClassReservationInfoResult result =
          sut.execute(new GetClassReservationInfoQuery(CLASS_ID));

      // then: 오늘 제외, 남은 월요일 3개(6/10, 6/17, 6/24)
      assertThat(result.availableDates()).hasSize(3);
      assertThat(result.availableDates())
          .allSatisfy(
              dateInfo -> {
                assertThat(dateInfo.availableTimes()).hasSize(1);
                assertThat(dateInfo.availableTimes().get(0).slotId()).isEqualTo(SLOT_ID);
                if (dateInfo.date().equals(nextMonday)) {
                  // 용량 5, 6/10 활성 예약 2 → 가용 3
                  assertThat(dateInfo.availableTimes().get(0).availableCapacity()).isEqualTo(3);
                } else {
                  // 그 외 날짜는 예약 0 → 가용 5
                  assertThat(dateInfo.availableTimes().get(0).availableCapacity()).isEqualTo(5);
                }
              });
    }

    @Test
    @DisplayName("[GRI-03] 활성 예약 수가 용량과 같으면 availableCapacity=0 반환")
    void 만석_슬롯() {
      // given: 용량 3, 다음주 월요일(6/10) 활성 예약 3
      // 오늘(6/3) 10:00는 clock(12:00) 기준 이미 지났으므로 제외됨.
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(activeClass()));
      given(loadClassSlotPort.findByClassId(CLASS_ID)).willReturn(List.of(mondaySlot(3)));
      java.time.LocalDate nextMonday = java.time.LocalDate.of(2024, 6, 10);
      given(
              getSlotReservationInfoUseCase.countActiveReservationsForDateRange(
                  org.mockito.ArgumentMatchers.eq(SLOT_ID),
                  org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.any()))
          .willReturn(java.util.Map.of(nextMonday, 3));

      // when
      GetClassReservationInfoResult result =
          sut.execute(new GetClassReservationInfoQuery(CLASS_ID));

      // then
      assertThat(result.availableDates())
          .anySatisfy(
              d -> {
                assertThat(d.date()).isEqualTo(nextMonday);
                assertThat(d.availableTimes().get(0).availableCapacity()).isZero();
              });
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[GRI-04] 존재하지 않는 classId로 조회 시 ClassNotFoundException 예외")
    void 존재하지_않는_클래스() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> sut.execute(new GetClassReservationInfoQuery(CLASS_ID)))
          .isInstanceOf(ClassNotFoundException.class);
    }
  }
}
