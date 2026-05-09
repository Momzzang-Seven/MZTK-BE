package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClassSummaryAdapter}.
 *
 * <p>The adapter now delegates to {@link GetClassInfoUseCase#findSummariesBySlotIds} — a single
 * bulk JPQL projection — instead of calling GetClassDetailUseCase per slot. Tests verify the
 * mapping, inactive-class filtering, and data-integrity fallback behaviours.
 */
@ExtendWith(MockitoExtension.class)
class ClassSummaryAdapterTest {

  @Mock private GetClassInfoUseCase getClassInfoUseCase;

  @InjectMocks private ClassSummaryAdapter sut;

  private ClassSummaryProjection activeProjection(Long classId, String title, int price) {
    return new ClassSummaryProjection(classId, 99L, title, price, true);
  }

  private ClassSummaryProjection inactiveProjection(Long classId) {
    return new ClassSummaryProjection(classId, 99L, "비활성 클래스", 50000, false);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // findBySlotId
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySlotId - 활성 클래스 슬롯이면 ClassSummary가 채워진다")
  void findBySlotId_ActiveClass_ReturnsSummary() {
    // given — findBySlotId delegates to findBySlotIds(List.of(slotId))
    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L)))
        .willReturn(Map.of(3L, activeProjection(1L, "요가 기초", 50000)));
    // thumbnail resolved via pre-built slotToClassId map (no duplicate JOIN)
    given(getClassInfoUseCase.loadThumbnailKeysBySlotToClassMap(Map.of(3L, 1L)))
        .willReturn(Map.of(3L, "thumb/yoga.jpg"));

    // when
    Optional<ClassSummary> result = sut.findBySlotId(3L);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("요가 기초");
    assertThat(result.get().priceAmount()).isEqualTo(50000);
    assertThat(result.get().thumbnailFinalObjectKey()).isEqualTo("thumb/yoga.jpg");
  }

  @Test
  @DisplayName("findBySlotId - 비활성 클래스이면 Optional.empty() 반환 (500 방지)")
  void findBySlotId_InactiveClass_ReturnsEmpty() {
    // given — inactive projection is filtered out inside findBySlotIds
    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L)))
        .willReturn(Map.of(3L, inactiveProjection(1L)));

    // when
    Optional<ClassSummary> result = sut.findBySlotId(3L);

    // then — inactive class must NOT cause 500
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findBySlotId - 슬롯이 존재하지 않으면 Optional.empty() 반환")
  void findBySlotId_SlotNotFound_ReturnsEmpty() {
    // given — port returns empty map (slot not found)
    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(99L))).willReturn(Map.of());

    // when
    Optional<ClassSummary> result = sut.findBySlotId(99L);

    // then
    assertThat(result).isEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // findBySlotIds (batch)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySlotIds - 여러 슬롯 중 inactive 슬롯은 제외하고 active만 반환")
  void findBySlotIds_MixedActiveAndInactive_ReturnsOnlyActive() {
    // given
    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L, 5L)))
        .willReturn(
            Map.of(
                3L, activeProjection(1L, "요가 기초", 50000),
                5L, inactiveProjection(2L)));
    // only slot 3 (active, classId=1) is included in the thumbnail lookup
    given(getClassInfoUseCase.loadThumbnailKeysBySlotToClassMap(Map.of(3L, 1L)))
        .willReturn(Map.of());

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L, 5L));

    // then — only slot 3 (active) is included
    assertThat(result).containsOnlyKeys(3L);
    assertThat(result.get(3L).title()).isEqualTo("요가 기초");
  }

  @Test
  @DisplayName("findBySlotIds - active 슬롯의 thumbnail이 loadThumbnailKeysBySlotToClassMap을 통해 채워진다")
  void findBySlotIds_ThumbnailResolved_ViaCachedClassIdMap() {
    // given — classId=1L for slotId=3L; adapter must NOT re-run the JOIN query
    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L)))
        .willReturn(Map.of(3L, activeProjection(1L, "필라테스", 40000)));
    given(getClassInfoUseCase.loadThumbnailKeysBySlotToClassMap(Map.of(3L, 1L)))
        .willReturn(Map.of(3L, "thumb/pilates.jpg"));

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L));

    // then — thumbnail key resolved without a duplicate JOIN
    assertThat(result).containsOnlyKeys(3L);
    assertThat(result.get(3L).thumbnailFinalObjectKey()).isEqualTo("thumb/pilates.jpg");
    // loadThumbnailKeysBySlotIds (the JOIN-repeating variant) must never be called
    verify(getClassInfoUseCase, never())
        .loadThumbnailKeysBySlotIds(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("findBySlotIds - 전체 슬롯이 inactive이면 thumbnail lookup을 건너뛴다")
  void findBySlotIds_AllInactive_SkipsThumbnailLookup() {
    // given — both slots inactive; slotToClassId map will be empty → no thumbnail call
    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L, 5L)))
        .willReturn(
            Map.of(
                3L, inactiveProjection(1L),
                5L, inactiveProjection(2L)));

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L, 5L));

    // then — result is empty and thumbnail port is never called
    assertThat(result).isEmpty();
    verify(getClassInfoUseCase, never())
        .loadThumbnailKeysBySlotToClassMap(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("findBySlotIds - 빈 슬롯 목록이면 DB 조회 없이 빈 맵 반환")
  void findBySlotIds_EmptyInput_ReturnsEmptyMap() {
    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of());

    // then — no port call should be made
    assertThat(result).isEmpty();
    verify(getClassInfoUseCase, never()).findSummariesBySlotIds(org.mockito.ArgumentMatchers.any());
  }

  // ──────────────────────────────────────────────────────────────────────────
  // ClassSummary compact constructor 검증
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("ClassSummary - priceAmount가 음수이면 IllegalStateException (도메인 불변 조건 위반)")
  void classSummary_NegativePriceAmount_ThrowsIllegalState() {
    assertThatThrownBy(() -> new ClassSummary("요가", -1, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("priceAmount must be > 0");
  }

  @Test
  @DisplayName("ClassSummary - priceAmount=0이면 IllegalStateException (도메인 정솵인 priceAmount > 0 위반)")
  void classSummary_ZeroPriceAmount_ThrowsIllegalState() {
    // priceAmount=0 is corrupt data; the DB constraint (price_amount > 0) and
    // MarketplaceClass domain invariant both prohibit free classes.
    assertThatThrownBy(() -> new ClassSummary("데스트 클래스", 0, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("priceAmount must be > 0");
  }

  // ──────────────────────────────────────────────────────────────────────────
  // data-integrity fallback (corrupt / legacy data)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySlotIds - priceAmount=0인 슬롯은 corrupt data로 해당 슬롯만 제외")
  void findBySlotIds_ZeroPrice_SkipsCorruptEntry() {
    // given — priceAmount=0 violates the price_amount > 0 DB constraint → treat as corrupt
    // Note: the corrupt projection has active=true, so it enters the slotToClassId map;
    //       it is only discarded when ClassSummary compact constructor throws.
    ClassSummaryProjection zeroPrice = new ClassSummaryProjection(1L, 99L, "제목없는수업", 0, true);
    ClassSummaryProjection valid = activeProjection(2L, "필라테스", 40000);

    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L, 7L)))
        .willReturn(Map.of(3L, zeroPrice, 7L, valid));
    // both active slots are in slotToClassId → thumbnail lookup receives both
    given(getClassInfoUseCase.loadThumbnailKeysBySlotToClassMap(Map.of(3L, 1L, 7L, 2L)))
        .willReturn(Map.of());

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L, 7L));

    // then — zero-price slot is skipped; valid slot is still returned
    assertThat(result).containsOnlyKeys(7L);
    assertThat(result.get(7L).title()).isEqualTo("필라테스");
  }

  @Test
  @DisplayName("findBySlotIds - corrupt data(priceAmount 음수)가 있어도 500이 아닌 해당 슬롯만 제외")
  void findBySlotIds_NegativePrice_SkipsCorruptEntry() {
    // given — negative priceAmount violates price_amount > 0 → corrupt data
    // Note: active=true → slot enters slotToClassId map; discard happens in ClassSummary ctor.
    ClassSummaryProjection corrupt = new ClassSummaryProjection(1L, 99L, "요가", -1, true);
    ClassSummaryProjection valid = activeProjection(2L, "필라테스", 40000);

    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L, 7L)))
        .willReturn(Map.of(3L, corrupt, 7L, valid));
    // both active slots enter slotToClassId → thumbnail lookup receives both
    given(getClassInfoUseCase.loadThumbnailKeysBySlotToClassMap(Map.of(3L, 1L, 7L, 2L)))
        .willReturn(Map.of());

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L, 7L));

    // then — corrupt slot is skipped; valid slot is still returned
    assertThat(result).containsOnlyKeys(7L);
    assertThat(result.get(7L).title()).isEqualTo("필라테스");
  }

  @Test
  @DisplayName("findBySlotIds - corrupt data(blank title)가 있어도 해당 슬롯만 제외")
  void findBySlotIds_BlankTitle_SkipsCorruptEntry() {
    // given
    ClassSummaryProjection blankTitle = new ClassSummaryProjection(1L, 99L, "   ", 50000, true);

    given(getClassInfoUseCase.findSummariesBySlotIds(List.of(3L)))
        .willReturn(Map.of(3L, blankTitle));

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L));

    // then
    assertThat(result).isEmpty();
  }
}
