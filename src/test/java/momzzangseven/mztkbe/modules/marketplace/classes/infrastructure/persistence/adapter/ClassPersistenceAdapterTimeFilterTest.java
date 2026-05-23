package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.MarketplaceClassEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * H2-based integration test for time-range filtering in {@link
 * ClassPersistenceAdapter#findActiveClasses}.
 *
 * <p>Validates that {@code startTime}/{@code endTime} String parameters are correctly parsed to
 * {@link LocalTime} and compared against the {@code class_slots.start_time} column. This tests the
 * QueryDSL path (non-DISTANCE sort). The native SQL path (DISTANCE sort) uses the same {@code
 * LocalTime.parse()} logic, so this test provides proxy coverage for the fix.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(ClassPersistenceAdapterSummaryTestConfig.class)
@DisplayName("ClassPersistenceAdapter — 시간 필터 H2 통합 테스트")
class ClassPersistenceAdapterTimeFilterTest {

  @Autowired private EntityManager em;
  @Autowired private ClassPersistenceAdapter sut;

  private static final Long TRAINER_ID = 1L;
  private static final PageRequest PAGE = PageRequest.of(0, 20);

  /** Persists a marketplace_classes row and returns its generated ID. */
  private Long saveClass(String title, boolean active) {
    MarketplaceClassEntity entity =
        MarketplaceClassEntity.builder()
            .trainerId(TRAINER_ID)
            .title(title)
            .category(ClassCategory.PT)
            .description("테스트 클래스")
            .priceAmount(50_000)
            .durationMinutes(60)
            .active(active)
            .version(0L)
            .build();
    em.persist(entity);
    em.flush();
    return entity.getId();
  }

  /** Persists a class_slots row linked to the given classId with the specified startTime. */
  private void saveSlot(Long classId, LocalTime startTime) {
    ClassSlotEntity slot =
        ClassSlotEntity.builder()
            .classId(classId)
            .daysOfWeek(List.of(java.time.DayOfWeek.MONDAY))
            .startTime(startTime)
            .capacity(5)
            .active(true)
            .build();
    em.persist(slot);
    em.flush();
  }

  @BeforeEach
  void clearPersistenceContext() {
    em.clear();
  }

  // ── Tests ──────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("시간 필터 정상 동작")
  class TimeFilterWorks {

    @Test
    @DisplayName("[TF-01] startTime 필터 → 해당 시간 이후 슬롯이 있는 클래스만 반환")
    void startTimeFilter_returnsClassesWithSlotsAfterStartTime() {
      // given: 10:00 슬롯 클래스, 14:00 슬롯 클래스
      Long morningClass = saveClass("오전 PT", true);
      saveSlot(morningClass, LocalTime.of(10, 0));

      Long afternoonClass = saveClass("오후 PT", true);
      saveSlot(afternoonClass, LocalTime.of(14, 0));

      // when: startTime=12:00 → 14:00 슬롯만 매칭
      Page<ClassItem> result =
          sut.findActiveClasses(null, null, null, "LATEST", null, "12:00", null, PAGE);

      // then
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).title()).isEqualTo("오후 PT");
    }

    @Test
    @DisplayName("[TF-02] endTime 필터 → 해당 시간 이전 슬롯이 있는 클래스만 반환")
    void endTimeFilter_returnsClassesWithSlotsBeforeEndTime() {
      // given
      Long morningClass = saveClass("오전 PT", true);
      saveSlot(morningClass, LocalTime.of(10, 0));

      Long afternoonClass = saveClass("오후 PT", true);
      saveSlot(afternoonClass, LocalTime.of(14, 0));

      // when: endTime=12:00 → 10:00 슬롯만 매칭 (startTime < endTime)
      Page<ClassItem> result =
          sut.findActiveClasses(null, null, null, "LATEST", null, null, "12:00", PAGE);

      // then
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).title()).isEqualTo("오전 PT");
    }

    @Test
    @DisplayName("[TF-03] startTime + endTime 범위 필터 → 범위 내 슬롯이 있는 클래스만 반환")
    void startAndEndTimeFilter_returnsClassesWithSlotsInRange() {
      // given: 09:00, 11:00, 15:00 슬롯
      Long earlyClass = saveClass("이른 오전 PT", true);
      saveSlot(earlyClass, LocalTime.of(9, 0));

      Long midClass = saveClass("오전 중간 PT", true);
      saveSlot(midClass, LocalTime.of(11, 0));

      Long lateClass = saveClass("오후 PT", true);
      saveSlot(lateClass, LocalTime.of(15, 0));

      // when: 10:00~13:00 → 11:00 슬롯만 매칭
      Page<ClassItem> result =
          sut.findActiveClasses(null, null, null, "LATEST", null, "10:00", "13:00", PAGE);

      // then
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).title()).isEqualTo("오전 중간 PT");
    }

    @Test
    @DisplayName("[TF-04] 시간 필터 없음 → 모든 활성 클래스 반환")
    void noTimeFilter_returnsAllActiveClasses() {
      // given
      Long class1 = saveClass("클래스 A", true);
      saveSlot(class1, LocalTime.of(10, 0));

      Long class2 = saveClass("클래스 B", true);
      saveSlot(class2, LocalTime.of(14, 0));

      Long inactiveClass = saveClass("비활성 클래스", false);
      saveSlot(inactiveClass, LocalTime.of(12, 0));

      // when: no time filter
      Page<ClassItem> result =
          sut.findActiveClasses(null, null, null, "LATEST", null, null, null, PAGE);

      // then: 활성 클래스만 2개
      assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("[TF-05] HH:mm:ss 형식의 시간 문자열도 정상 파싱된다")
    void timeWithSeconds_parsesCorrectly() {
      // given
      Long classId = saveClass("PT 클래스", true);
      saveSlot(classId, LocalTime.of(10, 30, 0));

      // when: "09:00:00" ~ "11:00:00" — HH:mm:ss 형식
      Page<ClassItem> result =
          sut.findActiveClasses(null, null, null, "LATEST", null, "09:00:00", "11:00:00", PAGE);

      // then
      assertThat(result.getContent()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("비활성 슬롯 제외")
  class InactiveSlotExcluded {

    @Test
    @DisplayName("[TF-06] 비활성 슬롯은 시간 필터 매칭에서 제외된다")
    void inactiveSlot_excludedFromTimeFilter() {
      // given: 활성 슬롯(09:00) + 비활성 슬롯(14:00)을 가진 클래스
      Long classId = saveClass("PT 클래스", true);
      saveSlot(classId, LocalTime.of(9, 0)); // 활성

      ClassSlotEntity inactiveSlot =
          ClassSlotEntity.builder()
              .classId(classId)
              .daysOfWeek(List.of(java.time.DayOfWeek.MONDAY))
              .startTime(LocalTime.of(14, 0))
              .capacity(5)
              .active(false) // 비활성
              .build();
      em.persist(inactiveSlot);
      em.flush();
      em.clear();

      // when: 13:00~15:00 필터 → 비활성 14:00 슬롯은 무시되어야 함
      Page<ClassItem> result =
          sut.findActiveClasses(null, null, null, "LATEST", null, "13:00", "15:00", PAGE);

      // then: 매칭되는 활성 슬롯 없으므로 빈 결과
      assertThat(result.getContent()).isEmpty();
    }
  }
}
