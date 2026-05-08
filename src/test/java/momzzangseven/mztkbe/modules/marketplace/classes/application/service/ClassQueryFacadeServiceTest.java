package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClassQueryFacadeService#findBySlotId}.
 *
 * <p>This method is the cross-module API surface used by the {@code reservation} module. It now
 * returns {@link ClassSummaryProjection} so that reservation callers never depend on the {@link
 * MarketplaceClass} aggregate. Covering key resolution paths ensures that projection mapping is
 * correct and reservation enrichment does not silently break.
 */
@DisplayName("ClassQueryFacadeService.findBySlotId() — slot → ClassSummaryProjection 리졸브 테스트")
@ExtendWith(MockitoExtension.class)
class ClassQueryFacadeServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private LoadClassSlotPort loadClassSlotPort;

  @InjectMocks private ClassQueryFacadeService sut;

  // ── helpers ───────────────────────────────────────────────────────────

  private ClassSlot slot(Long slotId, Long classId) {
    return ClassSlot.builder()
        .id(slotId)
        .classId(classId)
        .daysOfWeek(List.of(DayOfWeek.MONDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(5)
        .active(true)
        .build();
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
  @DisplayName("슬롯과 클래스가 모두 존재하면 ClassSummaryProjection을 반환한다")
  void findBySlotId_BothExist_ReturnsProjection() {
    // given
    given(loadClassSlotPort.findById(3L)).willReturn(Optional.of(slot(3L, 1L)));
    given(loadClassPort.findById(1L)).willReturn(Optional.of(cls(1L)));

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(3L);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().classId()).isEqualTo(1L);
    assertThat(result.get().title()).isEqualTo("요가 기초");
    assertThat(result.get().priceAmount()).isEqualTo(50000);
    assertThat(result.get().trainerId()).isEqualTo(100L);
    assertThat(result.get().active()).isTrue();
  }

  @Test
  @DisplayName("slotId에 해당하는 슬롯이 없으면 Optional.empty()를 반환한다")
  void findBySlotId_SlotNotFound_ReturnsEmpty() {
    // given
    given(loadClassSlotPort.findById(999L)).willReturn(Optional.empty());

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(999L);

    // then — slot 없으면 class 조회를 시도해선 안 됨
    assertThat(result).isEmpty();
    verify(loadClassPort, never()).findById(any());
  }

  @Test
  @DisplayName("슬롯은 존재하지만 연결된 classId로 클래스를 찾을 수 없으면 Optional.empty()를 반환한다")
  void findBySlotId_SlotExistsButClassNotFound_ReturnsEmpty() {
    // given
    given(loadClassSlotPort.findById(3L)).willReturn(Optional.of(slot(3L, 999L)));
    given(loadClassPort.findById(999L)).willReturn(Optional.empty());

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(3L);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("비활성 클래스도 projection에 active=false로 담아 반환한다 (active 필터는 caller 책임)")
  void findBySlotId_InactiveClass_ReturnsProjectionWithActiveFalse() {
    // given — the facade does NOT filter by active; filtering is the caller's responsibility
    MarketplaceClass inactiveCls = cls(1L).toBuilder().active(false).build();
    given(loadClassSlotPort.findById(3L)).willReturn(Optional.of(slot(3L, 1L)));
    given(loadClassPort.findById(1L)).willReturn(Optional.of(inactiveCls));

    // when
    Optional<ClassSummaryProjection> result = sut.findBySlotId(3L);

    // then — facade returns projection with active=false; ClassSummaryAdapter handles filtering
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
