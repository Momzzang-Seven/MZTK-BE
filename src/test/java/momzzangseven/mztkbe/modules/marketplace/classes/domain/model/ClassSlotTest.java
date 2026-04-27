package momzzangseven.mztkbe.modules.marketplace.classes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidSlotException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ClassSlot 충돌 감지 단위 테스트")
class ClassSlotTest {

  private static final int DURATION = 60;

  private static ClassSlot slotOn(DayOfWeek day, int hour, int minute) {
    return ClassSlot.create(1L, List.of(day), LocalTime.of(hour, minute), 5);
  }

  @Nested
  @DisplayName("기본 충돌 케이스")
  class BasicConflict {

    @Test
    @DisplayName("[M-9] 같은 요일 같은 시작 시간 → 충돌")
    void conflictsWith_SameDaySameTime_ReturnsTrue() {
      ClassSlot a = slotOn(DayOfWeek.MONDAY, 10, 0);
      ClassSlot b = slotOn(DayOfWeek.MONDAY, 10, 0);
      assertThat(a.conflictsWith(b, DURATION)).isTrue();
    }

    @Test
    @DisplayName("[M-10] 같은 요일 겹치는 시간 → 충돌")
    void conflictsWith_SameDayOverlapping_ReturnsTrue() {
      // a: 10:00-11:00, b: 10:30-11:30
      ClassSlot a = slotOn(DayOfWeek.MONDAY, 10, 0);
      ClassSlot b = slotOn(DayOfWeek.MONDAY, 10, 30);
      assertThat(a.conflictsWith(b, DURATION)).isTrue();
    }

    @Test
    @DisplayName("[M-11] 같은 요일 딱 붙어있는 시간 → 충돌 없음 (반열린 구간)")
    void conflictsWith_SameDayAdjacentNonOverlapping_ReturnsFalse() {
      // a: 10:00-11:00, b: 11:00-12:00  → 교집합 없음
      ClassSlot a = slotOn(DayOfWeek.MONDAY, 10, 0);
      ClassSlot b = slotOn(DayOfWeek.MONDAY, 11, 0);
      assertThat(a.conflictsWith(b, DURATION)).isFalse();
    }

    @Test
    @DisplayName("[M-12] 다른 요일 → 충돌 없음")
    void conflictsWith_DifferentDays_ReturnsFalse() {
      ClassSlot a = slotOn(DayOfWeek.MONDAY, 10, 0);
      ClassSlot b = slotOn(DayOfWeek.TUESDAY, 10, 0);
      assertThat(a.conflictsWith(b, DURATION)).isFalse();
    }
  }

  @Nested
  @DisplayName("자정 넘김 (Midnight-crossing) 케이스")
  class MidnightCrossingCases {

    @Test
    @DisplayName("[M-13] 일요일 23:30 (120분) wrap → 다음 월요일 01:00 슬롯과 충돌 감지")
    void conflictsWith_MidnightCrossing_ConflictDetected() {
      // slotA: SUNDAY 23:30, 120min → wrap segment [0, 90) in WCM
      // slotB: MONDAY 01:00, 60min  → WCM segment [60, 120)
      // [0, 90) ∩ [60, 120) = [60, 90) → 충돌
      ClassSlot a = ClassSlot.create(1L, List.of(DayOfWeek.SUNDAY), LocalTime.of(23, 30), 5);
      ClassSlot b = ClassSlot.create(1L, List.of(DayOfWeek.MONDAY), LocalTime.of(1, 0), 5);
      assertThat(a.conflictsWith(b, 120)).isTrue();
    }

    @Test
    @DisplayName("[M-14] 일요일 23:30 (120분) wrap → 다음 월요일 02:00 슬롯과 충돌 없음")
    void conflictsWith_MidnightCrossingNoOverlap_ReturnsFalse() {
      // slotA: SUNDAY 23:30, 120min → wrap segment [0, 90) in WCM
      // slotB: MONDAY 02:00, 60min  → WCM segment [120, 180)
      // [0, 90) ∩ [120, 180) = {} → 충돌 없음
      ClassSlot a = ClassSlot.create(1L, List.of(DayOfWeek.SUNDAY), LocalTime.of(23, 30), 5);
      ClassSlot b = ClassSlot.create(1L, List.of(DayOfWeek.MONDAY), LocalTime.of(2, 0), 5);
      assertThat(a.conflictsWith(b, 120)).isFalse();
    }

    @Test
    @DisplayName("금요일 23:50 (30분) — 자정 직전 슬롯, 인접(00:20) 슬롯과 충돌 없음")
    void conflictsWith_FridayLateNoWrap_HandledCorrectly() {
      // slotA: FRIDAY 23:50, 30min → WCM [7190, 7220)
      // slotB: SATURDAY 00:20,  30min → WCM [7220, 7250) — 딱 붙어있으나 교집합 없음 (반열린 구간)
      ClassSlot a = ClassSlot.create(1L, List.of(DayOfWeek.FRIDAY), LocalTime.of(23, 50), 5);
      ClassSlot b = ClassSlot.create(1L, List.of(DayOfWeek.SATURDAY), LocalTime.of(0, 20), 5);
      assertThat(a.conflictsWith(b, 30)).isFalse();
    }
  }

  @Nested
  @DisplayName("다중 요일 슬롯 케이스")
  class MultiDayCases {

    @Test
    @DisplayName("A가 MON+WED, B가 WED+FRI → 수요일 겹침으로 충돌")
    void conflictsWith_MultiDaySharedDay_ReturnsTrue() {
      ClassSlot a =
          ClassSlot.create(
              1L, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), 5);
      ClassSlot b =
          ClassSlot.create(
              1L, List.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(10, 0), 5);
      assertThat(a.conflictsWith(b, DURATION)).isTrue();
    }

    @Test
    @DisplayName("A가 MON+WED, B가 TUE+THU → 공유 요일 없음, 충돌 없음")
    void conflictsWith_MultiDayNoSharedDay_ReturnsFalse() {
      ClassSlot a =
          ClassSlot.create(
              1L, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), 5);
      ClassSlot b =
          ClassSlot.create(
              1L, List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), LocalTime.of(10, 0), 5);
      assertThat(a.conflictsWith(b, DURATION)).isFalse();
    }
  }

  @Nested
  @DisplayName("daysOfWeek 중복 검증")
  class DaysOfWeekDuplicateValidation {

    @Test
    @DisplayName("중복 없는 요일 리스트 → 정상 생성")
    void create_DistinctDays_NoException() {
      ClassSlot slot =
          ClassSlot.create(
              1L, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), 5);
      assertThat(slot.getDaysOfWeek()).hasSize(2);
    }

    @Test
    @DisplayName("같은 요일 두 번 → MarketplaceInvalidSlotException")
    void create_DuplicateDays_ThrowsException() {
      assertThatThrownBy(
              () ->
                  ClassSlot.create(
                      1L, List.of(DayOfWeek.MONDAY, DayOfWeek.MONDAY), LocalTime.of(10, 0), 5))
          .isInstanceOf(MarketplaceInvalidSlotException.class)
          .hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("세 요일 중 하나가 중복 → MarketplaceInvalidSlotException")
    void create_ThreeDaysOneDuplicate_ThrowsException() {
      assertThatThrownBy(
              () ->
                  ClassSlot.create(
                      1L,
                      List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.MONDAY),
                      LocalTime.of(10, 0),
                      5))
          .isInstanceOf(MarketplaceInvalidSlotException.class);
    }
  }
}
