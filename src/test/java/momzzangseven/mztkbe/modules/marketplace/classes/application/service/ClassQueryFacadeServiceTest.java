package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClassQueryFacadeService}.
 *
 * <p>{@code findBySlotId} now delegates to {@link
 * LoadClassPort#findSummaryProjectionsBySlotIds} (single JOIN query) rather than performing a
 * 2-hop slot→class lookup. Tests verify the new delegation path and boundary behaviours.
 */
@DisplayName("ClassQueryFacadeService — findBySlotId / findById 리졸브 테스트")
@ExtendWith(MockitoExtension.class)
class ClassQueryFacadeServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private LoadClassSlotPort loadClassSlotPort; // used only by findByIdWithLock

  @InjectMocks private ClassQueryFacadeService sut;

  // ── helpers ───────────────────────────────────────────────────────────

  private ClassSummaryProjection projection(Long classId, String title, int price, boolean active) {
    return new ClassSummaryProjection(classId, 100L, title, price, active);
  }

  private MarketplaceClass cls(Long classId) {
    return MarketplaceClass.builder()
        .id(classId)
        .trainerId(100L)
        .title("요가 기초")
        .category(ClassCategory.YOGA)
        .description("입문자를 위한 요가 수업입니다.")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  // ── findBySlotId ──────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySlotId - 슬롯이 존재하면 단건 배치 조회로 Projection을 반환한다")
  void findBySlotId_SlotExists_ReturnsProjectionViaBatchQuery() {
    // given — facade calls findSummaryProjectionsBySlotIds(List.of(3L))
    given(loadClassPort.findSummaryProjectionsBySlotIds(List.of(3L)))
        .willReturn(Map.of(3L, projection(1L, "요가 기초", 50000, true)));

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(3L);

    // then — single DB round-trip; no LoadClassSlotPort.findById call
    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("요가 기초");
    assertThat(result.get().priceAmount()).isEqualTo(50000);
    assertThat(result.get().trainerId()).isEqualTo(100L);
    assertThat(result.get().active()).isTrue();
    verify(loadClassSlotPort, never()).findById(any());
  }

  @Test
  @DisplayName("findBySlotId - 슬롯이 존재하지 않으면 Optional.empty()를 반환한다")
  void findBySlotId_SlotNotFound_ReturnsEmpty() {
    // given — batch query returns empty map (slot not found in JOIN result)
    given(loadClassPort.findSummaryProjectionsBySlotIds(List.of(999L))).willReturn(Map.of());

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(999L);

    // then
    assertThat(result).isEmpty();
    verify(loadClassSlotPort, never()).findById(any());
  }

  @Test
  @DisplayName("findBySlotId - 비활성 클래스도 active=false로 Projection에 담아 반환한다 (필터는 caller 책임)")
  void findBySlotId_InactiveClass_ReturnsProjectionWithActiveFalse() {
    // given — facade does NOT filter; ClassSummaryAdapter handles active filtering
    given(loadClassPort.findSummaryProjectionsBySlotIds(List.of(3L)))
        .willReturn(Map.of(3L, projection(1L, "비활성 클래스", 50000, false)));

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(3L);

    // then — facade returns projection with active=false unchanged
    assertThat(result).isPresent();
    assertThat(result.get().active()).isFalse();
  }

  // ── findById ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("findById - 존재하는 classId이면 해당 클래스를 반환한다")
  void findById_ExistingClass_ReturnsClass() {
    given(loadClassPort.findById(1L)).willReturn(Optional.of(cls(1L)));

    Optional<MarketplaceClass> result = sut.findById(1L);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("findById - 존재하지 않는 classId이면 Optional.empty()를 반환한다")
  void findById_NonExistentClass_ReturnsEmpty() {
    given(loadClassPort.findById(999L)).willReturn(Optional.empty());

    Optional<MarketplaceClass> result = sut.findById(999L);

    assertThat(result).isEmpty();
  }

  // ── helpers for verify ───────────────────────────────────────────────

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}
