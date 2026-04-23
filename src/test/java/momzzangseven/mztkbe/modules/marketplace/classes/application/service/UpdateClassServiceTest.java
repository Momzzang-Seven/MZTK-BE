package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadSlotReservationPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.junit.jupiter.api.BeforeEach;
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
  @Mock private LoadSlotReservationPort loadSlotReservationPort;
  @Mock private ManageClassTagPort manageClassTagPort;
  @Mock private UpdateClassImagesPort updateClassImagesPort;

  @InjectMocks private UpdateClassService updateClassService;

  // ========================================================
  // 상수
  // ========================================================

  private static final Long TRAINER_ID = 1L;
  private static final Long OTHER_TRAINER_ID = 99L;
  private static final Long CLASS_ID = 10L;
  private static final Long SLOT_ID = 100L;

  // ========================================================
  // 픽스처 헬퍼
  // ========================================================

  private static MarketplaceClass validClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("기존 클래스")
        .category(ClassCategory.PT)
        .description("기존 설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  private static ClassSlot activeSlot(Long slotId) {
    return ClassSlot.builder()
        .id(slotId)
        .classId(CLASS_ID)
        .daysOfWeek(List.of(DayOfWeek.MONDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(5)
        .active(true)
        .build();
  }

  /**
   * 기존 슬롯 수정 커맨드 (timeId 명시). UpdateClassCommand 필드 순서: trainerId, classId, title, category,
   * description, priceAmount, durationMinutes, tags, features, personalItems, imageIds, classTimes
   */
  private static UpdateClassCommand updateExistingSlotCommand(Long timeId) {
    return new UpdateClassCommand(
        TRAINER_ID,
        CLASS_ID,
        "수정된 제목",
        ClassCategory.PT,
        "수정된 설명",
        60000,
        60,
        List.of("태그1"),
        List.of("특징1"),
        null,
        null,
        List.of(new ClassTimeCommand(timeId, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5)));
  }

  /** 신규 슬롯만 포함하는 커맨드 (timeId = null) */
  private static UpdateClassCommand addNewSlotCommand() {
    return new UpdateClassCommand(
        TRAINER_ID,
        CLASS_ID,
        "수정된 제목",
        ClassCategory.PT,
        "수정된 설명",
        60000,
        60,
        null,
        null,
        null,
        null,
        List.of(new ClassTimeCommand(null, List.of(DayOfWeek.TUESDAY), LocalTime.of(14, 0), 3)));
  }

  // ========================================================
  // 성공 케이스
  // ========================================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @BeforeEach
    void commonGiven() {
      // lenient: 모든 성공 테스트에서 공통으로 필요한 stub — 사용하지 않는 테스트에서도 오류 없이 무시
      lenient().when(loadClassPort.findById(CLASS_ID)).thenReturn(Optional.of(validClass()));
      lenient().when(saveClassPort.save(any())).thenReturn(validClass());
    }

    @Test
    @DisplayName("[U-01] 기존 슬롯 수정 → saveAll 호출, 동일 classId 반환")
    void execute_UpdateExistingSlot_SavesAndReturnsClassId() {
      // given: 기존 슬롯 존재, 활성 예약 없음
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(activeSlot(SLOT_ID)));
      given(loadSlotReservationPort.countActiveReservationsIn(java.util.List.of(SLOT_ID)))
          .willReturn(java.util.Map.of(SLOT_ID, 0));

      // when
      UpdateClassResult result = updateClassService.execute(updateExistingSlotCommand(SLOT_ID));

      // then
      assertThat(result.classId()).isEqualTo(CLASS_ID);
      verify(saveClassSlotPort).saveAll(any());
    }

    @Test
    @DisplayName("[U-02] 신규 슬롯 추가 (timeId=null) → 기존 슬롯 없어도 saveAll 호출")
    void execute_AddNewSlot_SavesNewSlot() {
      // given: 기존 active 슬롯 없음
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID)).willReturn(List.of());

      // when
      updateClassService.execute(addNewSlotCommand());

      // then
      verify(saveClassSlotPort).saveAll(any());
    }

    @Test
    @DisplayName("[U-03] 예약 이력 없는 슬롯 누락 시 hard-delete (deleteById 호출)")
    void execute_SlotRemovedWithNoHistory_HardDeletes() {
      // given: SLOT_ID 존재, request에는 없음 → 삭제 대상
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(activeSlot(SLOT_ID)));
      // 삭제 대상 슬롯: 활성 예약 없음, 이력도 없음
      given(loadSlotReservationPort.countActiveReservationsIn(java.util.List.of(SLOT_ID)))
          .willReturn(java.util.Map.of(SLOT_ID, 0));
      given(loadSlotReservationPort.hasAnyReservationHistory(SLOT_ID)).willReturn(false);

      // addNewSlotCommand: SLOT_ID 없는 커맨드 (SLOT_ID → 삭제 대상)
      updateClassService.execute(addNewSlotCommand());

      // then: hard-delete
      verify(saveClassSlotPort).deleteById(SLOT_ID);
    }

    @Test
    @DisplayName("[U-04] 예약 이력만 있는 슬롯 누락 시 soft-delete (deleteById 미호출)")
    void execute_SlotRemovedWithHistory_SoftDeletes() {
      // given
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(activeSlot(SLOT_ID)));
      given(loadSlotReservationPort.countActiveReservationsIn(java.util.List.of(SLOT_ID)))
          .willReturn(java.util.Map.of(SLOT_ID, 0));
      given(loadSlotReservationPort.hasAnyReservationHistory(SLOT_ID)).willReturn(true);

      updateClassService.execute(addNewSlotCommand());

      // then: saveAll에 soft-deleted slot 포함, deleteById 미호출
      verify(saveClassSlotPort).saveAll(any());
      verify(saveClassSlotPort, never()).deleteById(anyLong());
    }
  }

  // ========================================================
  // 실패 케이스
  // ========================================================

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[U-05] 존재하지 않는 classId → ClassNotFoundException, save 미호출")
    void execute_ClassNotFound_ThrowsClassNotFoundException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(updateExistingSlotCommand(SLOT_ID)))
          .isInstanceOf(ClassNotFoundException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("[U-06] 소유자가 아닌 트레이너 수정 시도 → MarketplaceUnauthorizedAccessException")
    void execute_WrongOwner_ThrowsUnauthorizedException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(validClass()));

      UpdateClassCommand wrongOwnerCmd =
          new UpdateClassCommand(
              OTHER_TRAINER_ID, // 소유자 아님
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
              List.of(
                  new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5)));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(wrongOwnerCmd))
          .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("[U-07] 활성 예약 있는 슬롯 삭제 시도 → SlotHasActiveReservationException")
    void execute_RemoveSlotWithActiveReservation_ThrowsException() {
      // given: SLOT_ID 존재 & request에 없음 → 삭제 시도
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(validClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(activeSlot(SLOT_ID)));
      given(loadSlotReservationPort.countActiveReservationsIn(java.util.List.of(SLOT_ID)))
          .willReturn(java.util.Map.of(SLOT_ID, 2)); // 활성 예약 2건

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(addNewSlotCommand()))
          .isInstanceOf(SlotHasActiveReservationException.class);
      verify(saveClassSlotPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("[U-08] 용량을 활성 예약 수 미만으로 축소 → CapacityShorterThanReservationsException")
    void execute_CapacityBelowActiveReservations_ThrowsException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(validClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID))
          .willReturn(List.of(activeSlot(SLOT_ID)));
      given(loadSlotReservationPort.countActiveReservationsIn(java.util.List.of(SLOT_ID)))
          .willReturn(java.util.Map.of(SLOT_ID, 4)); // 예약 4건

      // 기존 슬롯을 capacity=2로 축소 시도 (4명 예약 중 → 불가)
      UpdateClassCommand reducedCapacityCmd =
          new UpdateClassCommand(
              TRAINER_ID,
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
              List.of(
                  new ClassTimeCommand(
                      SLOT_ID, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 2)));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(reducedCapacityCmd))
          .isInstanceOf(CapacityShorterThanReservationsException.class);
    }

    @Test
    @DisplayName("[U-09] 새 슬롯들 간 시간 충돌 → SlotTimeConflictException")
    void execute_ConflictingNewSlots_ThrowsSlotTimeConflictException() {
      // given: 기존 슬롯 없음
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(validClass()));
      given(loadClassSlotPort.findByClassIdWithLock(CLASS_ID)).willReturn(List.of());

      // 동일 요일 10:00 + 10:30, duration=60 → 충돌
      UpdateClassCommand conflictCmd =
          new UpdateClassCommand(
              TRAINER_ID,
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
              List.of(
                  new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5),
                  new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 30), 5)));

      // when & then
      assertThatThrownBy(() -> updateClassService.execute(conflictCmd))
          .isInstanceOf(SlotTimeConflictException.class);
      verify(saveClassSlotPort, never()).saveAll(any());
    }
  }
}
