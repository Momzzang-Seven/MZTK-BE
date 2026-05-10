package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.MarketplaceClassEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * H2-based adapter test for {@link ClassPersistenceAdapter#findSummaryProjectionsBySlotIds}.
 *
 * <p>Verifies that the JPQL JOIN query ({@code class_slots JOIN marketplace_classes}) returns
 * correct {@link ClassSummaryProjection} records when given various slot ID sets. All other {@link
 * ClassPersistenceAdapter} dependencies (QueryDSL, LoadClassTagPort, LoadTrainerStorePort) are not
 * needed for this query path and are excluded from the test context via a minimal {@link @Import}
 * configuration.
 *
 * <p>Test scenarios:
 *
 * <ul>
 *   <li>Happy path: multiple slot IDs → correct slotId→projection mapping
 *   <li>Missing slot: unknown slotId absent from result map
 *   <li>Inactive class: {@code active=false} returned as-is (filtering is caller's responsibility)
 *   <li>Title/price projection: values match what was persisted
 *   <li>Empty input: no DB round-trip, returns empty map immediately
 * </ul>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(ClassPersistenceAdapterSummaryTestConfig.class)
@DisplayName("ClassPersistenceAdapter — findSummaryProjectionsBySlotIds H2 통합 테스트")
class ClassPersistenceAdapterSummaryTest {

  @Autowired private EntityManager em;
  @Autowired private ClassPersistenceAdapter sut;

  // ── Fixtures ──────────────────────────────────────────────────────────

  private static final Long TRAINER_ID = 1L;

  /**
   * Persists a {@code marketplace_classes} row and returns its generated ID. version=0 is required
   * because the entity uses {@code @Version}.
   */
  private Long saveClass(String title, int price, boolean active) {
    MarketplaceClassEntity entity =
        MarketplaceClassEntity.builder()
            .trainerId(TRAINER_ID)
            .title(title)
            .category(momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory.YOGA)
            .description("테스트 클래스")
            .priceAmount(price)
            .durationMinutes(60)
            .active(active)
            .version(0L)
            .build();
    em.persist(entity);
    em.flush();
    return entity.getId();
  }

  /**
   * Persists a {@code class_slots} row linked to the given classId and returns its generated ID.
   */
  private Long saveSlot(Long classId) {
    ClassSlotEntity slot =
        ClassSlotEntity.builder()
            .classId(classId)
            .daysOfWeek(List.of(java.time.DayOfWeek.MONDAY))
            .startTime(LocalTime.of(10, 0))
            .capacity(5)
            .active(true)
            .build();
    em.persist(slot);
    em.flush();
    return slot.getId();
  }

  @BeforeEach
  void clearPersistenceContext() {
    em.clear(); // ensure no L1-cache hits mask real DB reads
  }

  // ── Tests ──────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("정상 매핑")
  class HappyPath {

    @Test
    @DisplayName("단일 슬롯 ID → 올바른 Projection이 slotId 키로 반환된다")
    void singleSlotId_returnsMappedProjection() {
      Long classId = saveClass("요가 기초", 50_000, true);
      Long slotId = saveSlot(classId);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(slotId));

      assertThat(result).containsOnlyKeys(slotId);
      ClassSummaryProjection proj = result.get(slotId);
      assertThat(proj.classId()).isEqualTo(classId);
      assertThat(proj.trainerId()).isEqualTo(TRAINER_ID);
      assertThat(proj.title()).isEqualTo("요가 기초");
      assertThat(proj.priceAmount()).isEqualTo(50_000);
      assertThat(proj.active()).isTrue();
    }

    @Test
    @DisplayName("복수 슬롯 ID → 각각 독립적으로 매핑된다")
    void multipleSlotIds_eachMappedIndependently() {
      Long classA = saveClass("필라테스 입문", 40_000, true);
      Long classB = saveClass("크로스핏 기초", 60_000, true);
      Long slotA = saveSlot(classA);
      Long slotB = saveSlot(classB);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(slotA, slotB));

      assertThat(result).hasSize(2).containsKeys(slotA, slotB);

      ClassSummaryProjection pa = result.get(slotA);
      assertThat(pa.title()).isEqualTo("필라테스 입문");
      assertThat(pa.priceAmount()).isEqualTo(40_000);

      ClassSummaryProjection pb = result.get(slotB);
      assertThat(pb.title()).isEqualTo("크로스핏 기초");
      assertThat(pb.priceAmount()).isEqualTo(60_000);
    }

    @Test
    @DisplayName("같은 클래스의 여러 슬롯 → 슬롯마다 동일한 classId/title이 매핑된다")
    void multipleSlotsForSameClass_eachReturnsSameClassData() {
      Long classId = saveClass("수영 초급", 35_000, true);
      Long slot1 = saveSlot(classId);
      Long slot2 = saveSlot(classId);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(slot1, slot2));

      assertThat(result).hasSize(2);
      assertThat(result.get(slot1).classId()).isEqualTo(classId);
      assertThat(result.get(slot2).classId()).isEqualTo(classId);
      assertThat(result.get(slot1).title()).isEqualTo("수영 초급");
      assertThat(result.get(slot2).title()).isEqualTo("수영 초급");
    }
  }

  @Nested
  @DisplayName("누락 슬롯")
  class MissingSlot {

    @Test
    @DisplayName("존재하지 않는 slotId → 결과 맵에서 해당 키만 누락된다")
    void unknownSlotId_absentFromResultMap() {
      Long classId = saveClass("테니스 기초", 45_000, true);
      Long existingSlotId = saveSlot(classId);
      Long nonExistentSlotId = 999_999L;

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(existingSlotId, nonExistentSlotId));

      assertThat(result).containsOnlyKeys(existingSlotId);
      assertThat(result).doesNotContainKey(nonExistentSlotId);
    }

    @Test
    @DisplayName("모든 slotId가 존재하지 않으면 빈 맵을 반환한다")
    void allSlotIdsUnknown_returnsEmptyMap() {
      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(888L, 999L));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("비활성 클래스")
  class InactiveClass {

    @Test
    @DisplayName("비활성 클래스의 슬롯도 active=false로 Projection에 포함된다 (필터는 어댑터 바깥 책임)")
    void inactiveClass_includedWithActiveFalse() {
      Long classId = saveClass("운영 종료 클래스", 20_000, false); // inactive
      Long slotId = saveSlot(classId);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(slotId));

      assertThat(result).containsKey(slotId);
      assertThat(result.get(slotId).active()).isFalse();
      assertThat(result.get(slotId).title()).isEqualTo("운영 종료 클래스");
    }

    @Test
    @DisplayName("활성/비활성 클래스 혼재 시 둘 다 반환된다")
    void mixedActiveInactive_bothIncluded() {
      Long activeClass = saveClass("활성 클래스", 30_000, true);
      Long inactiveClass = saveClass("비활성 클래스", 25_000, false);
      Long activeSlot = saveSlot(activeClass);
      Long inactiveSlot = saveSlot(inactiveClass);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(activeSlot, inactiveSlot));

      assertThat(result).hasSize(2);
      assertThat(result.get(activeSlot).active()).isTrue();
      assertThat(result.get(inactiveSlot).active()).isFalse();
    }
  }

  @Nested
  @DisplayName("빈 입력")
  class EmptyInput {

    @Test
    @DisplayName("빈 리스트 → DB 조회 없이 빈 맵을 즉시 반환한다")
    void emptySlotIds_returnsEmptyMapImmediately() {
      Map<Long, ClassSummaryProjection> result = sut.findSummaryProjectionsBySlotIds(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 입력 → 빈 맵을 반환한다")
    void nullSlotIds_returnsEmptyMap() {
      Map<Long, ClassSummaryProjection> result = sut.findSummaryProjectionsBySlotIds(null);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("가격/제목 프로젝션 정밀도")
  class ProjectionValues {

    @Test
    @DisplayName("가격이 0인 무료 클래스도 priceAmount=0으로 정확히 매핑된다")
    void freeClass_priceAmountIsZero() {
      Long classId = saveClass("무료 체험 클래스", 0, true);
      Long slotId = saveSlot(classId);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(slotId));

      assertThat(result.get(slotId).priceAmount()).isZero();
    }

    @Test
    @DisplayName("특수문자 포함 제목도 손상 없이 반환된다")
    void specialCharTitle_preservedExactly() {
      String specialTitle = "PT & 스트레칭 (주 3회) — 입문";
      Long classId = saveClass(specialTitle, 55_000, true);
      Long slotId = saveSlot(classId);

      Map<Long, ClassSummaryProjection> result =
          sut.findSummaryProjectionsBySlotIds(List.of(slotId));

      assertThat(result.get(slotId).title()).isEqualTo(specialTitle);
    }
  }
}
