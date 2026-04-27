package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetTrainerClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetTrainerClassesResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.LoadTrainerSanctionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetTrainerClassesService 단위 테스트")
class GetTrainerClassesServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private LoadClassImagesPort loadClassImagesPort;
  @Mock private LoadTrainerSanctionPort loadTrainerSanctionPort;

  @InjectMocks private GetTrainerClassesService getTrainerClassesService;

  // ========================================================
  // Helpers
  // ========================================================

  private static final Long TRAINER_ID = 1L;

  private static MarketplaceClass stubClass(Long id, boolean active) {
    return MarketplaceClass.builder()
        .id(id)
        .trainerId(TRAINER_ID)
        .title("PT 클래스 " + id)
        .category(ClassCategory.PT)
        .description("설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(active)
        .build();
  }

  private static GetTrainerClassesQuery query() {
    return new GetTrainerClassesQuery(TRAINER_ID, 0);
  }

  // ========================================================
  // 제재 상태 케이스
  // ========================================================

  @Nested
  @DisplayName("제재 상태 반영")
  class SanctionStatusCases {

    @Test
    @DisplayName("[GT-01] 제재 없는 트레이너 → isSuspended=false, suspendedUntil=null")
    void execute_NotSuspended_ReturnsFalseAndNullUntil() {
      // given
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of()));

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      assertThat(result.isSuspended()).isFalse();
      assertThat(result.suspendedUntil()).isNull();
      // 제재 없을 때는 getSuspendedUntil 호출 안 해야 함
      verify(loadTrainerSanctionPort, never()).getSuspendedUntil(TRAINER_ID);
    }

    @Test
    @DisplayName("[GT-02] 제재 중인 트레이너 → isSuspended=true, suspendedUntil 값 존재")
    void execute_Suspended_ReturnsTrueAndSuspendedUntil() {
      // given
      LocalDateTime until = LocalDateTime.of(2025, 12, 31, 23, 59);
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(true);
      given(loadTrainerSanctionPort.getSuspendedUntil(TRAINER_ID)).willReturn(Optional.of(until));
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of()));

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      assertThat(result.isSuspended()).isTrue();
      assertThat(result.suspendedUntil()).isEqualTo(until);
    }

    @Test
    @DisplayName("[GT-03] 제재 중이나 getSuspendedUntil이 empty → suspendedUntil=null")
    void execute_SuspendedButNoUntilDate_ReturnsNull() {
      // given
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(true);
      given(loadTrainerSanctionPort.getSuspendedUntil(TRAINER_ID)).willReturn(Optional.empty());
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of()));

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      assertThat(result.isSuspended()).isTrue();
      assertThat(result.suspendedUntil()).isNull();
    }
  }

  // ========================================================
  // 클래스 목록 + 배치 조회 케이스
  // ========================================================

  @Nested
  @DisplayName("클래스 목록 및 배치 조회")
  class ClassListCases {

    @Test
    @DisplayName("[GT-04] 클래스 없는 트레이너 → 빈 items, 썸네일 조회 미호출")
    void execute_NoClasses_EmptyItemsAndNoThumbnailCall() {
      // given
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of()));

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      assertThat(result.items()).isEmpty();
      verify(loadClassImagesPort, never()).loadThumbnailKeys(anyList());
    }

    @Test
    @DisplayName("[GT-05] 클래스 있을 때 → loadThumbnailKeys 1회 호출, items 개수 일치")
    void execute_WithClasses_LoadsThumbnailsOnce() {
      // given
      MarketplaceClass c1 = stubClass(1L, true);
      MarketplaceClass c2 = stubClass(2L, false);
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(c1, c2)));
      given(loadClassImagesPort.loadThumbnailKeys(anyList()))
          .willReturn(Map.of(1L, "thumb/abc.webp", 2L, "thumb/def.webp"));

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      assertThat(result.items()).hasSize(2);
      verify(loadClassImagesPort, times(1)).loadThumbnailKeys(anyList());
    }

    @Test
    @DisplayName("[GT-06] TrainerClassItem 필드 — active 플래그와 썸네일 키 올바르게 매핑")
    void execute_WithClasses_ItemFieldsMappedCorrectly() {
      // given
      MarketplaceClass c1 = stubClass(1L, true); // active
      MarketplaceClass c2 = stubClass(2L, false); // inactive
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(c1, c2)));
      given(loadClassImagesPort.loadThumbnailKeys(anyList()))
          .willReturn(Map.of(1L, "thumb/class1.webp"));

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      GetTrainerClassesResult.TrainerClassItem item1 =
          result.items().stream().filter(i -> i.classId().equals(1L)).findFirst().orElseThrow();
      GetTrainerClassesResult.TrainerClassItem item2 =
          result.items().stream().filter(i -> i.classId().equals(2L)).findFirst().orElseThrow();

      assertThat(item1.active()).isTrue();
      assertThat(item1.thumbnailFinalObjectKey()).isEqualTo("thumb/class1.webp");
      assertThat(item2.active()).isFalse();
      assertThat(item2.thumbnailFinalObjectKey()).isNull(); // 썸네일 없음
    }

    @Test
    @DisplayName("[GT-07] 페이지 메타 정보 정확히 반환")
    void execute_PageMetadata_PassedThrough() {
      // given
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      Page<MarketplaceClass> page =
          new PageImpl<>(
              List.of(stubClass(1L, true)),
              org.springframework.data.domain.PageRequest.of(0, 20),
              1L);
      given(loadClassPort.findByTrainerId(eq(TRAINER_ID), any(Pageable.class))).willReturn(page);
      given(loadClassImagesPort.loadThumbnailKeys(anyList())).willReturn(Map.of());

      // when
      GetTrainerClassesResult result = getTrainerClassesService.execute(query());

      // then
      assertThat(result.currentPage()).isZero();
      assertThat(result.totalPages()).isEqualTo(1);
      assertThat(result.totalElements()).isEqualTo(1L);
    }
  }
}
