package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassSummaryAdapterTest {

  @Mock private GetClassInfoUseCase getClassInfoUseCase;
  @Mock private GetClassDetailUseCase getClassDetailUseCase;

  @InjectMocks private ClassSummaryAdapter sut;

  /** Minimal MarketplaceClass stub — only id is needed by the adapter. */
  private MarketplaceClass classWithId(Long classId) {
    return MarketplaceClass.builder().id(classId).build();
  }

  /** Minimal GetClassDetailResult stub. */
  private GetClassDetailResult detailResult(String title, int price, String thumb) {
    return new GetClassDetailResult(
        1L, 99L, null, title, null, null, price, thumb, List.of(), List.of(), List.of(), 60, null,
        List.of());
  }

  // ──────────────────────────────────────────────────────────────────────────
  // findBySlotId
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySlotId - 활성 클래스 슬롯이면 ClassSummary가 채워진다")
  void findBySlotId_ActiveClass_ReturnsSummary() {
    // given
    given(getClassInfoUseCase.findBySlotId(3L)).willReturn(Optional.of(classWithId(1L)));
    given(getClassDetailUseCase.execute(new GetClassDetailQuery(1L)))
        .willReturn(detailResult("요가 기초", 50000, "thumb/key.jpg"));

    // when
    Optional<ClassSummary> result = sut.findBySlotId(3L);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("요가 기초");
    assertThat(result.get().priceAmount()).isEqualTo(50000);
    assertThat(result.get().thumbnailFinalObjectKey()).isEqualTo("thumb/key.jpg");
  }

  @Test
  @DisplayName("findBySlotId - 비활성 클래스(ClassNotFoundException)이면 Optional.empty() 반환 (500 방지)")
  void findBySlotId_InactiveClass_ReturnsEmpty() {
    // given — slot resolves to a class, but GetClassDetailUseCase throws because active=false
    given(getClassInfoUseCase.findBySlotId(3L)).willReturn(Optional.of(classWithId(1L)));
    given(getClassDetailUseCase.execute(new GetClassDetailQuery(1L)))
        .willThrow(new ClassNotFoundException(1L));

    // when
    Optional<ClassSummary> result = sut.findBySlotId(3L);

    // then — exception must be absorbed; must NOT propagate as 500
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findBySlotId - 슬롯이 존재하지 않으면 Optional.empty() 반환")
  void findBySlotId_SlotNotFound_ReturnsEmpty() {
    // given
    given(getClassInfoUseCase.findBySlotId(99L)).willReturn(Optional.empty());

    // when
    Optional<ClassSummary> result = sut.findBySlotId(99L);

    // then
    assertThat(result).isEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // findBySlotIds (batch)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySlotIds - 여러 슬롯 중 일부는 inactive여도 나머지 슬롯의 summary는 정상 반환")
  void findBySlotIds_MixedActiveAndInactive_ReturnsOnlyActive() {
    // given — slot 3 is active, slot 5's class is inactive
    given(getClassInfoUseCase.findBySlotId(3L)).willReturn(Optional.of(classWithId(1L)));
    given(getClassDetailUseCase.execute(new GetClassDetailQuery(1L)))
        .willReturn(detailResult("요가 기초", 50000, "thumb/a.jpg"));

    given(getClassInfoUseCase.findBySlotId(5L)).willReturn(Optional.of(classWithId(2L)));
    given(getClassDetailUseCase.execute(new GetClassDetailQuery(2L)))
        .willThrow(new ClassNotFoundException(2L));

    // when
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of(3L, 5L));

    // then
    assertThat(result).containsOnlyKeys(3L);
    assertThat(result.get(3L).title()).isEqualTo("요가 기초");
  }

  @Test
  @DisplayName("findBySlotIds - 빈 슬롯 목록이면 빈 맵 반환")
  void findBySlotIds_EmptyInput_ReturnsEmptyMap() {
    Map<Long, ClassSummary> result = sut.findBySlotIds(List.of());
    assertThat(result).isEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // ClassSummary compact constructor 검증
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("ClassSummary - priceAmount가 0이면 IllegalStateException (도메인 불변 조건 위반)")
  void classSummary_ZeroPriceAmount_ThrowsIllegalState() {
    assertThatThrownBy(() -> new ClassSummary("요가", 0, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("priceAmount must be > 0");
  }

  @Test
  @DisplayName("ClassSummary - priceAmount가 음수이면 IllegalStateException (도메인 불변 조건 위반)")
  void classSummary_NegativePriceAmount_ThrowsIllegalState() {
    assertThatThrownBy(() -> new ClassSummary("요가", -1000, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("priceAmount must be > 0");
  }
}
