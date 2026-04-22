package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.SlotTimeConflictException;
import momzzangseven.mztkbe.global.error.marketplace.StoreNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.RegisterClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.RegisterClassResult;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadTrainerStorePort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterClassService 단위 테스트")
class RegisterClassServiceTest {

  @Mock private LoadTrainerStorePort loadTrainerStorePort;
  @Mock private LoadTrainerSanctionPort loadTrainerSanctionPort;
  @Mock private SaveClassPort saveClassPort;
  @Mock private SaveClassSlotPort saveClassSlotPort;
  @Mock private UpdateClassImagesPort updateClassImagesPort;
  @Mock private ManageClassTagPort manageClassTagPort;
  // NOTE: LoadClassSlotPort is NOT used by RegisterClassService — removed to avoid Mockito strict
  // stub warning

  @InjectMocks private RegisterClassService registerClassService;

  // ========================================================
  // Helpers
  // ========================================================

  private static final Long TRAINER_ID = 1L;
  private static final Long CLASS_ID = 10L;

  private static RegisterClassCommand validCommand() {
    return new RegisterClassCommand(
        TRAINER_ID,
        "PT 60분 기초",
        ClassCategory.PT,
        "기초 체력 향상 PT 클래스",
        50000,
        60,
        List.of("다이어트"),
        List.of("1:1 맞춤"),
        null,
        null,
        List.of(new ClassTimeCommand(null, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), 5)));
  }

  private static MarketplaceClass savedClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("PT 60분 기초")
        .category(ClassCategory.PT)
        .description("기초 체력 향상 PT 클래스")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  private static TrainerStore mockStore() {
    return TrainerStore.builder()
        .id(1L)
        .trainerId(TRAINER_ID)
        .storeName("테스트 스토어")
        .address("서울시 강남구 테헤란로 1")
        .detailAddress("101호")
        .latitude(37.5)
        .longitude(127.0)
        .phoneNumber("010-1234-5678")
        .build();
  }

  // ========================================================
  // 성공 케이스
  // ========================================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-15] 유효한 커맨드 → 클래스, 슬롯, 태그 저장 후 classId 반환")
    void execute_ValidCommand_SavesClassAndSlots() {
      // given
      given(loadTrainerStorePort.findByTrainerId(TRAINER_ID)).willReturn(Optional.of(mockStore()));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(saveClassPort.save(any())).willReturn(savedClass());

      // when
      RegisterClassResult result = registerClassService.execute(validCommand());

      // then
      assertThat(result.classId()).isEqualTo(CLASS_ID);
      verify(saveClassPort, times(1)).save(any());
      verify(saveClassSlotPort, times(1)).saveAll(any());
      verify(manageClassTagPort, times(1)).linkTagsToClass(eq(CLASS_ID), any());
    }
  }

  // ========================================================
  // 실패 케이스
  // ========================================================

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-16] 스토어 미존재 → StoreNotFoundException, save 호출 없음")
    void execute_NoStore_ThrowsStoreNotFoundException() {
      // given
      given(loadTrainerStorePort.findByTrainerId(TRAINER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> registerClassService.execute(validCommand()))
          .isInstanceOf(StoreNotFoundException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("[M-17] 제재 중인 트레이너 → TrainerSuspendedException")
    void execute_TrainerSuspended_ThrowsTrainerSuspendedException() {
      // given
      given(loadTrainerStorePort.findByTrainerId(TRAINER_ID)).willReturn(Optional.of(mockStore()));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(true);

      // when & then
      assertThatThrownBy(() -> registerClassService.execute(validCommand()))
          .isInstanceOf(TrainerSuspendedException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("[M-18] 슬롯 시간 충돌 → SlotTimeConflictException, save 호출 없음")
    void execute_SlotTimeConflict_ThrowsSlotTimeConflictException() {
      // given
      given(loadTrainerStorePort.findByTrainerId(TRAINER_ID)).willReturn(Optional.of(mockStore()));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);

      // 동일 요일, 겹치는 시간 슬롯 2개 (10:00 + 10:30, duration=60 → 겹침)
      RegisterClassCommand conflictCommand =
          new RegisterClassCommand(
              TRAINER_ID,
              "PT 60분 기초",
              ClassCategory.PT,
              "기초 체력 향상",
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
      assertThatThrownBy(() -> registerClassService.execute(conflictCommand))
          .isInstanceOf(SlotTimeConflictException.class);
      verify(saveClassSlotPort, never()).saveAll(any());
    }
  }
}
