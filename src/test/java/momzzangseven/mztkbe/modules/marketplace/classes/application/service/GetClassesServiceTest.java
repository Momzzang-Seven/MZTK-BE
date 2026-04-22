package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassesResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetClassesService 단위 테스트")
class GetClassesServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private LoadClassImagesPort loadClassImagesPort;
  @Mock private LoadClassTagPort loadClassTagPort;

  @InjectMocks private GetClassesService getClassesService;

  // ========================================================
  // Helpers
  // ========================================================

  private static ClassItem stub(Long classId) {
    return new ClassItem(classId, "PT 클래스", ClassCategory.PT, 50000, 60, null, List.of(), null);
  }

  private static Page<ClassItem> pageOf(ClassItem... items) {
    return new PageImpl<>(List.of(items));
  }

  // ========================================================
  // 정렬 폴백 케이스
  // ========================================================

  @Nested
  @DisplayName("sort 폴백 로직")
  class SortFallbackCases {

    @Test
    @DisplayName("[G-01] sort=DISTANCE + lat/lng 없음 → RATING으로 전달")
    void execute_DistanceSortWithoutLocation_FallsBackToRating() {
      // given
      GetClassesQuery query =
          new GetClassesQuery(null, null, null, "DISTANCE", null, null, null, 0);
      given(
              loadClassPort.findActiveClasses(
                  any(), any(), any(), eq("RATING"), any(), any(), any(), any()))
          .willReturn(pageOf());

      // when
      GetClassesResult result = getClassesService.execute(query);

      // then: 빈 결과 + RATING으로 어댑터 호출됨
      assertThat(result.items()).isEmpty();
      verify(loadClassPort)
          .findActiveClasses(eq(null), eq(null), any(), eq("RATING"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[G-02] sort=null + lat/lng 있음 → DISTANCE로 전달 (스펙: 위치정보 있을 때 기본 DISTANCE)")
    void execute_NullSortWithLocation_DefaultsToDistance() {
      // given
      GetClassesQuery query = new GetClassesQuery(37.5, 127.0, null, null, null, null, null, 0);
      given(
              loadClassPort.findActiveClasses(
                  any(), any(), any(), eq("DISTANCE"), any(), any(), any(), any()))
          .willReturn(pageOf());

      // when
      getClassesService.execute(query);

      // then
      verify(loadClassPort)
          .findActiveClasses(
              eq(37.5), eq(127.0), any(), eq("DISTANCE"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[G-03] sort=null + lat/lng 없음 → RATING으로 전달")
    void execute_NullSortWithoutLocation_DefaultsToRating() {
      // given
      GetClassesQuery query = new GetClassesQuery(null, null, null, null, null, null, null, 0);
      given(
              loadClassPort.findActiveClasses(
                  any(), any(), any(), eq("RATING"), any(), any(), any(), any()))
          .willReturn(pageOf());

      // when
      getClassesService.execute(query);

      // then
      verify(loadClassPort)
          .findActiveClasses(eq(null), eq(null), any(), eq("RATING"), any(), any(), any(), any());
    }
  }

  // ========================================================
  // 배치 조회 케이스
  // ========================================================

  @Nested
  @DisplayName("배치 조회")
  class BatchLoadCases {

    @Test
    @DisplayName("[G-04] 결과 없을 때 → 태그/썸네일 배치 조회 미호출, 빈 결과 반환")
    void execute_EmptyPage_NoBatchLoads() {
      // given
      GetClassesQuery query = new GetClassesQuery(null, null, null, "LATEST", null, null, null, 0);
      given(loadClassPort.findActiveClasses(any(), any(), any(), any(), any(), any(), any(), any()))
          .willReturn(pageOf());

      // when
      GetClassesResult result = getClassesService.execute(query);

      // then
      assertThat(result.items()).isEmpty();
      verify(loadClassTagPort, never()).findTagsByClassIdsIn(anyList());
      verify(loadClassImagesPort, never()).loadThumbnailKeys(anyList());
    }

    @Test
    @DisplayName("[G-05] 결과 있을 때 → 태그·썸네일 배치 조회 각 1회 + classIds 전달 확인")
    void execute_NonEmptyPage_BatchLoadsTagsAndThumbnails() {
      // given
      GetClassesQuery query = new GetClassesQuery(null, null, null, "LATEST", null, null, null, 0);
      ClassItem item1 = stub(1L);
      ClassItem item2 = stub(2L);
      given(loadClassPort.findActiveClasses(any(), any(), any(), any(), any(), any(), any(), any()))
          .willReturn(pageOf(item1, item2));
      given(loadClassTagPort.findTagsByClassIdsIn(anyList()))
          .willReturn(Map.of(1L, List.of("다이어트"), 2L, List.of("근력")));
      given(loadClassImagesPort.loadThumbnailKeys(anyList()))
          .willReturn(Map.of(1L, "thumb/abc.webp"));

      // when
      GetClassesResult result = getClassesService.execute(query);

      // then: 2개 항목, 배치 조회 각 1회
      assertThat(result.items()).hasSize(2);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
      verify(loadClassTagPort).findTagsByClassIdsIn(captor.capture());
      assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
      verify(loadClassImagesPort).loadThumbnailKeys(anyList());
    }

    @Test
    @DisplayName("[G-06] 페이지 메타 정보(totalElements, items 수) 정확히 반환")
    void execute_PageMetadata_PassedThrough() {
      // given: 0번 페이지, 전체 45건
      GetClassesQuery query = new GetClassesQuery(null, null, null, "LATEST", null, null, null, 0);
      ClassItem item1 = stub(10L);
      ClassItem item2 = stub(20L);
      Page<ClassItem> page =
          new PageImpl<>(
              List.of(item1, item2), org.springframework.data.domain.PageRequest.of(0, 20), 45L);
      given(loadClassPort.findActiveClasses(any(), any(), any(), any(), any(), any(), any(), any()))
          .willReturn(page);
      given(loadClassTagPort.findTagsByClassIdsIn(anyList())).willReturn(Map.of());
      given(loadClassImagesPort.loadThumbnailKeys(anyList())).willReturn(Map.of());

      // when
      GetClassesResult result = getClassesService.execute(query);

      // then
      assertThat(result.items()).hasSize(2);
      assertThat(result.currentPage()).isZero();
      assertThat(result.totalElements()).isEqualTo(45L);
      assertThat(result.totalPages()).isEqualTo(3); // ceil(45/20)
    }
  }
}
