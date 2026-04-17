package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassSlotInfo;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassImagesPort.ClassImages;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetClassDetailService 단위 테스트")
class GetClassDetailServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private LoadClassImagesPort loadClassImagesPort;
  @Mock private LoadClassSlotPort loadClassSlotPort;

  @InjectMocks private GetClassDetailService getClassDetailService;

  private static final Long CLASS_ID = 10L;
  private static final Long TRAINER_ID = 1L;
  private static final Long STORE_ID = 100L;
  private static final Long SLOT_ID = 5L;

  private static ClassDetailInfo stubDetailInfo() {
    return new ClassDetailInfo(
        CLASS_ID,
        TRAINER_ID,
        STORE_ID,
        "테스트 스토어",
        "서울 강남구",
        "1층",
        37.5172,
        127.0473,
        "PT 클래스",
        "PT",
        "개인 PT 클래스입니다.",
        80000,
        60,
        List.of("다이어트", "근력"),
        List.of("체중 감량 경험"),
        "운동화, 수건",
        List.of());
  }

  private static ClassSlot activeSlot() {
    return ClassSlot.builder()
        .id(SLOT_ID)
        .classId(CLASS_ID)
        .daysOfWeek(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(5)
        .active(true)
        .build();
  }

  private static ClassSlot inactiveSlot() {
    return ClassSlot.builder()
        .id(99L)
        .classId(CLASS_ID)
        .daysOfWeek(List.of(DayOfWeek.FRIDAY))
        .startTime(LocalTime.of(18, 0))
        .capacity(3)
        .active(false)
        .build();
  }

  // ========================================================
  // 성공 케이스
  // ========================================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("정상 조회 시 스토어 정보, 이미지, active 슬롯이 모두 포함된 결과 반환")
    void execute_ValidClassId_ReturnsFullDetailWithActiveSlots() {
      // given
      ClassImages images = new ClassImages("thumb/key.jpg", List.of());
      given(loadClassPort.findClassDetailById(CLASS_ID)).willReturn(Optional.of(stubDetailInfo()));
      given(loadClassImagesPort.loadImages(CLASS_ID)).willReturn(images);
      given(loadClassSlotPort.findByClassId(CLASS_ID))
          .willReturn(List.of(activeSlot(), inactiveSlot()));

      // when
      GetClassDetailResult result =
          getClassDetailService.execute(new GetClassDetailQuery(CLASS_ID));

      // then
      assertThat(result.classId()).isEqualTo(CLASS_ID);
      assertThat(result.store().storeId()).isEqualTo(STORE_ID);
      assertThat(result.store().storeName()).isEqualTo("테스트 스토어");
      assertThat(result.thumbnailFinalObjectKey()).isEqualTo("thumb/key.jpg");
      assertThat(result.tags()).containsExactly("다이어트", "근력");

      // inactive 슬롯은 제외되어야 함
      assertThat(result.classTimes()).hasSize(1);
      ClassSlotInfo slotInfo = result.classTimes().get(0);
      assertThat(slotInfo.timeId()).isEqualTo(SLOT_ID);
      assertThat(slotInfo.startTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("active 슬롯이 없을 때 classTimes는 빈 리스트 반환")
    void execute_NoActiveSlots_ReturnsEmptyClassTimes() {
      // given
      ClassImages images = new ClassImages(null, List.of());
      given(loadClassPort.findClassDetailById(CLASS_ID)).willReturn(Optional.of(stubDetailInfo()));
      given(loadClassImagesPort.loadImages(CLASS_ID)).willReturn(images);
      // inactive 슬롯만 존재
      given(loadClassSlotPort.findByClassId(CLASS_ID)).willReturn(List.of(inactiveSlot()));

      // when
      GetClassDetailResult result =
          getClassDetailService.execute(new GetClassDetailQuery(CLASS_ID));

      // then
      assertThat(result.classTimes()).isEmpty();
    }

    @Test
    @DisplayName("스토어 미등록 트레이너의 클래스 조회 시 store 필드는 null 값 포함")
    void execute_NoStore_ReturnsNullStoreFields() {
      // given — store 정보가 없는 ClassDetailInfo
      ClassDetailInfo noStoreDetail =
          new ClassDetailInfo(
              CLASS_ID,
              TRAINER_ID,
              null,
              null,
              null,
              null,
              null,
              null,
              "PT 클래스",
              "PT",
              "설명",
              50000,
              60,
              List.of(),
              List.of(),
              null,
              List.of());

      ClassImages images = new ClassImages(null, List.of());
      given(loadClassPort.findClassDetailById(CLASS_ID)).willReturn(Optional.of(noStoreDetail));
      given(loadClassImagesPort.loadImages(CLASS_ID)).willReturn(images);
      given(loadClassSlotPort.findByClassId(CLASS_ID)).willReturn(List.of());

      // when
      GetClassDetailResult result =
          getClassDetailService.execute(new GetClassDetailQuery(CLASS_ID));

      // then
      assertThat(result.store().storeId()).isNull();
      assertThat(result.store().storeName()).isNull();
    }
  }

  // ========================================================
  // 실패 케이스
  // ========================================================

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("존재하지 않는 classId 조회 시 ClassNotFoundException 발생")
    void execute_ClassNotFound_ThrowsClassNotFoundException() {
      // given
      given(loadClassPort.findClassDetailById(CLASS_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> getClassDetailService.execute(new GetClassDetailQuery(CLASS_ID)))
          .isInstanceOf(ClassNotFoundException.class);

      verify(loadClassSlotPort, never()).findByClassId(CLASS_ID);
    }
  }
}
