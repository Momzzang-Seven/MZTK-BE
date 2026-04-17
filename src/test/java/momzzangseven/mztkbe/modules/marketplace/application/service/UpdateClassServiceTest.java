package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.CapacityShorterThanReservationsException;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.SlotHasActiveReservationException;
import momzzangseven.mztkbe.global.error.marketplace.SlotTimeConflictException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadSlotReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateClassService 단위 테스트")
class UpdateClassServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private SaveClassPort saveClassPort;
  @Mock private LoadClassSlotPort loadClassSlotPort;
  @Mock private SaveClassSlotPort saveClassSlotPort;
  @Mock private UpdateClassImagesPort updateClassImagesPort;
  @Mock private ManageClassTagPort manageClassTagPort;
  @Mock private LoadSlotReservationPort loadSlotReservationPort;

  @InjectMocks private UpdateClassService updateClassService;

  // ========================================================
  // Helpers
  // ========================================================

  private static final Long TRAINER_ID = 1L;
  private static final Long CLASS_ID = 10L;
  private static final Long SLOT_ID = 5L;

  private static MarketplaceClass existingClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("원래 제목")
        .category(ClassCategory.PT)
        .description("원래 설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  private static MarketplaceClass savedClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("수정 제목")
        .category(ClassCategory.PT)
        .description("수정 설명")
        .priceAmount(60000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  private static ClassSlot existingSlot() {
    return ClassSlot.builder()
        .id(SLOT_ID)
        .classId(CLASS_ID)
        .daysOfWeek(List.of(DayOfWeek.MONDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(5)
        .active(true)
        .build();
  }

  private static ClassSlot existingSlotWithCapacity(int capacity) {
    return ClassSlot.builder()
        .id(SLOT_ID)
        .classId(CLASS_ID)
        .daysOfWeek(List.of(DayOfWeek.MONDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(capacity)
        .active(true)
        .build();
  }

  private static UpdateClassCommand validCommand(List<ClassTimeCommand> classTimes) {
    return new UpdateClassCommand(
        TRAINER_ID,
        CLASS_ID,
        "수정 제목",
        ClassCategory.PT,
        "수정 설명",
        60000,
        60,
        List.of("다이어트"),
        null,
        null,
        null,
        classTimes);
  }

  // ========================================================
  // 성공 케이스
  // ========================================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("소유자가 정상 수정 요청 시 메타데이터, 슬롯, 태그 모두 저장")
    void execute_ValidUpdate_UpdatesMetadataAndSlots() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(existingClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID)).willReturn(List.of(existingSlot()));
      given(loadSlotReservationPort.countActiveReservations(SLOT_ID)).willReturn(0);
      given(saveClassPort.save(any())).willReturn(savedClass());

      // 기존 슬롯 수정 (timeId=5L) + 신규 슬롯 1개 (timeId=null)
      List<ClassTimeCommand> classTimes =
          List.of(
              new ClassTimeCommand(SLOT_ID, List.of(DayOfWeek.TUESDAY), LocalTime.of(9, 0), 8),
              new ClassTimeCommand(null, List.of(DayOfWeek.WEDNESDAY), LocalTime.of(14, 0), 5));

      // when
      updateClassService.execute(validCommand(classTimes));

      // then
      verify(loadClassSlotPort, times(1)).findByClassIdWithLock(CLASS_ID); // [M-19] 비관락 호출 확인
      verify(saveClassPort, times(1)).save(any());
      verify(saveClassSlotPort, times(1)).saveAll(anyList());
      verify(manageClassTagPort, times(1)).updateTags(CLASS_ID, List.of("다이어트"));
    }

    @Test
    @DisplayName("capacity를 늘릴 때도 예약수 확인 후 정상 처리 (예약수 미만이면 허용)")
    void execute_CapacityIncrease_ProceedsWithReservationCheck() {
      // given — 기존 capacity=3, 현재 예약=2, 새 capacity=10 (예약수보다 크므로 허용)
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(existingClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(existingSlotWithCapacity(3)));
      given(loadSlotReservationPort.countActiveReservations(SLOT_ID)).willReturn(2);
      given(saveClassPort.save(any())).willReturn(savedClass());

      List<ClassTimeCommand> classTimes =
          List.of(
              new ClassTimeCommand(SLOT_ID, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 10));

      // when
      updateClassService.execute(validCommand(classTimes));

      // then — countActiveReservations는 capacity와 무관하게 항상 호출
      verify(loadSlotReservationPort, times(1)).countActiveReservations(SLOT_ID);
      verify(saveClassPort, times(1)).save(any());
    }
  }

  // ========================================================
  // 실패 케이스
  // ========================================================

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("소유권 불일치 시 MarketplaceUnauthorizedAccessException 발생, save 호출 없음")
    void execute_UnauthorizedTrainer_ThrowsMarketplaceUnauthorizedAccessException() {
      // given — 클래스는 trainerId=99 소유
      given(loadClassPort.findById(CLASS_ID))
          .willReturn(
              Optional.of(
                  MarketplaceClass.builder()
                      .id(CLASS_ID)
                      .trainerId(99L)
                      .title("제목")
                      .category(ClassCategory.PT)
                      .description("설명")
                      .priceAmount(50000)
                      .durationMinutes(60)
                      .active(true)
                      .build()));

      List<ClassTimeCommand> classTimes =
          List.of(new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5));

      // when & then — trainerId=1L로 요청 → 소유권 불일치
      assertThatThrownBy(
              () ->
                  updateClassService.execute(
                      new UpdateClassCommand(
                          1L,
                          CLASS_ID,
                          "제목",
                          ClassCategory.PT,
                          "설명",
                          50000,
                          60,
                          null,
                          null,
                          null,
                          null,
                          classTimes)))
          .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("슬롯 충돌 시 SlotTimeConflictException 발생, saveAll 호출 없음")
    void execute_SlotConflict_ThrowsSlotTimeConflictException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(existingClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID)).willReturn(List.of());

      // MONDAY 10:00 + MONDAY 10:30 (60분 → 겹침)
      List<ClassTimeCommand> conflictingSlots =
          List.of(
              new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5),
              new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 30), 5));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(validCommand(conflictingSlots)))
          .isInstanceOf(SlotTimeConflictException.class);
      verify(saveClassSlotPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("클래스 미존재 시 ClassNotFoundException 발생, save 호출 없음")
    void execute_ClassNotFound_ThrowsClassNotFoundException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.empty());

      List<ClassTimeCommand> classTimes =
          List.of(new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(validCommand(classTimes)))
          .isInstanceOf(ClassNotFoundException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("활성 예약 있는 슬롯 삭제 요청 시 SlotHasActiveReservationException 발생")
    void execute_DeleteSlotWithActiveReservation_ThrowsSlotHasActiveReservationException() {
      // given — 기존 슬롯(SLOT_ID)이 있고, 요청에는 해당 슬롯 없음(삭제 대상) + 활성 예약 존재
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(existingClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID)).willReturn(List.of(existingSlot()));
      given(loadSlotReservationPort.hasActiveReservation(SLOT_ID)).willReturn(true);

      // 요청 슬롯 목록에 SLOT_ID 없음 → soft-delete 시도
      List<ClassTimeCommand> classTimes =
          List.of(new ClassTimeCommand(null, List.of(DayOfWeek.WEDNESDAY), LocalTime.of(14, 0), 5));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(validCommand(classTimes)))
          .isInstanceOf(SlotHasActiveReservationException.class);
      verify(saveClassSlotPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("capacity를 현재 예약수 미만으로 감소 시 CapacityShorterThanReservationsException 발생")
    void execute_CapacityBelowActiveReservations_ThrowsCapacityShorterThanReservationsException() {
      // given — 기존 capacity=10, 현재 예약수=7, 새 capacity=5 (예약수 미만)
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(existingClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(existingSlotWithCapacity(10)));
      given(loadSlotReservationPort.countActiveReservations(SLOT_ID)).willReturn(7);

      List<ClassTimeCommand> classTimes =
          List.of(new ClassTimeCommand(SLOT_ID, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(validCommand(classTimes)))
          .isInstanceOf(CapacityShorterThanReservationsException.class);
      verify(saveClassSlotPort, never()).saveAll(any());
    }
  }
}
