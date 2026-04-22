package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter;

import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter.ClassSlotPersistenceAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.times;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository.ClassSlotJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassSlotPersistenceAdapter 단위 테스트")
class ClassSlotPersistenceAdapterTest {

  @Mock private ClassSlotJpaRepository classSlotJpaRepository;

  @InjectMocks private ClassSlotPersistenceAdapter classSlotPersistenceAdapter;

  // ============================================
  // Test Fixtures
  // ============================================

  private static final Long CLASS_ID = 10L;
  private static final Long SLOT_ID = 5L;

  private static ClassSlotEntity buildEntity(Long id, Long classId) {
    return ClassSlotEntity.builder()
        .id(id)
        .classId(classId)
        .daysOfWeek(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        .startTime(LocalTime.of(10, 0))
        .capacity(5)
        .active(true)
        .build();
  }

  private static ClassSlot buildDomain(Long id, Long classId) {
    return ClassSlot.builder()
        .id(id)
        .classId(classId)
        .daysOfWeek(List.of(DayOfWeek.MONDAY))
        .startTime(LocalTime.of(14, 0))
        .capacity(3)
        .active(true)
        .build();
  }

  // ============================================
  // findByClassId() — LoadClassSlotPort
  // ============================================

  @Nested
  @DisplayName("findByClassId() — LoadClassSlotPort 구현")
  class FindByClassIdTests {

    @Test
    @DisplayName("classId에 해당하는 슬롯 목록을 도메인 모델 리스트로 반환한다")
    void findByClassId_returnsDomainList_whenSlotsExist() {
      // given
      ClassSlotEntity entity = buildEntity(SLOT_ID, CLASS_ID);
      given(classSlotJpaRepository.findByClassId(CLASS_ID)).willReturn(List.of(entity));

      // when
      List<ClassSlot> result = classSlotPersistenceAdapter.findByClassId(CLASS_ID);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getId()).isEqualTo(SLOT_ID);
      assertThat(result.get(0).getClassId()).isEqualTo(CLASS_ID);
      assertThat(result.get(0).getDaysOfWeek())
          .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
      assertThat(result.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
      assertThat(result.get(0).getCapacity()).isEqualTo(5);
      assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("슬롯이 없으면 빈 리스트를 반환한다")
    void findByClassId_returnsEmptyList_whenNoSlots() {
      // given
      given(classSlotJpaRepository.findByClassId(CLASS_ID)).willReturn(List.of());

      // when
      List<ClassSlot> result = classSlotPersistenceAdapter.findByClassId(CLASS_ID);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JpaRepository.findByClassId()를 정확히 1번 호출한다")
    void findByClassId_callsRepositoryOnce() {
      // given
      given(classSlotJpaRepository.findByClassId(CLASS_ID)).willReturn(List.of());

      // when
      classSlotPersistenceAdapter.findByClassId(CLASS_ID);

      // then
      then(classSlotJpaRepository).should(times(1)).findByClassId(CLASS_ID);
    }
  }

  // ============================================
  // findByClassIdWithLock() — LoadClassSlotPort
  // ============================================

  @Nested
  @DisplayName("findByClassIdWithLock() — 비관락 슬롯 조회")
  class FindByClassIdWithLockTests {

    @Test
    @DisplayName("비관락 조회 시 도메인 모델 리스트로 반환한다")
    void findByClassIdWithLock_returnsDomainList() {
      // given
      ClassSlotEntity entity = buildEntity(SLOT_ID, CLASS_ID);
      given(classSlotJpaRepository.findByClassIdWithLock(CLASS_ID)).willReturn(List.of(entity));

      // when
      List<ClassSlot> result = classSlotPersistenceAdapter.findByClassIdWithLock(CLASS_ID);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getId()).isEqualTo(SLOT_ID);
    }

    @Test
    @DisplayName("비관락 조회 시 JpaRepository.findByClassIdWithLock()를 1번 호출한다")
    void findByClassIdWithLock_callsLockQueryOnce() {
      // given
      given(classSlotJpaRepository.findByClassIdWithLock(CLASS_ID)).willReturn(List.of());

      // when
      classSlotPersistenceAdapter.findByClassIdWithLock(CLASS_ID);

      // then
      then(classSlotJpaRepository).should(times(1)).findByClassIdWithLock(CLASS_ID);
      then(classSlotJpaRepository).should(times(0)).findByClassId(CLASS_ID);
    }
  }

  // ============================================
  // saveAll() — SaveClassSlotPort
  // ============================================

  @Nested
  @DisplayName("saveAll() — SaveClassSlotPort 구현")
  class SaveAllTests {

    @Test
    @DisplayName("도메인 슬롯 리스트를 Entity로 변환하여 저장하고 도메인 리스트로 반환한다")
    void saveAll_convertsDomainListAndReturns() {
      // given
      ClassSlot toSave = buildDomain(null, CLASS_ID);
      ClassSlotEntity savedEntity = buildEntity(SLOT_ID, CLASS_ID);
      given(classSlotJpaRepository.saveAll(any())).willReturn(List.of(savedEntity));

      // when
      List<ClassSlot> result = classSlotPersistenceAdapter.saveAll(List.of(toSave));

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getId()).isEqualTo(SLOT_ID);
      assertThat(result.get(0).getClassId()).isEqualTo(CLASS_ID);
    }

    @Test
    @DisplayName("JpaRepository.saveAll()을 정확히 1번 호출한다")
    void saveAll_callsRepositorySaveAllOnce() {
      // given
      ClassSlot toSave = buildDomain(null, CLASS_ID);
      ClassSlotEntity savedEntity = buildEntity(SLOT_ID, CLASS_ID);
      given(classSlotJpaRepository.saveAll(any())).willReturn(List.of(savedEntity));

      // when
      classSlotPersistenceAdapter.saveAll(List.of(toSave));

      // then
      then(classSlotJpaRepository).should(times(1)).saveAll(any());
    }

    @Test
    @DisplayName("빈 슬롯 리스트를 저장하면 빈 리스트를 반환한다")
    void saveAll_emptyList_returnsEmpty() {
      // given
      given(classSlotJpaRepository.saveAll(any())).willReturn(List.of());

      // when
      List<ClassSlot> result = classSlotPersistenceAdapter.saveAll(List.of());

      // then
      assertThat(result).isEmpty();
    }
  }

  // ============================================
  // deleteById() — SaveClassSlotPort
  // ============================================

  @Nested
  @DisplayName("deleteById() — SaveClassSlotPort 구현")
  class DeleteByIdTests {

    @Test
    @DisplayName("JpaRepository.deleteById()를 정확히 1번 호출한다")
    void deleteById_callsRepositoryOnce() {
      // given
      willDoNothing().given(classSlotJpaRepository).deleteById(SLOT_ID);

      // when
      classSlotPersistenceAdapter.deleteById(SLOT_ID);

      // then
      then(classSlotJpaRepository).should(times(1)).deleteById(SLOT_ID);
    }
  }

  // ============================================
  // Entity ↔ Domain 변환 검증
  // ============================================

  @Nested
  @DisplayName("Entity ↔ Domain 변환 검증")
  class MappingTests {

    @Test
    @DisplayName("ClassSlotEntity → ClassSlot 변환 시 모든 필드가 올바르게 매핑된다")
    void toDomain_mapsAllFields() {
      // given
      ClassSlotEntity entity = buildEntity(SLOT_ID, CLASS_ID);

      // when
      ClassSlot domain = entity.toDomain();

      // then
      assertThat(domain.getId()).isEqualTo(SLOT_ID);
      assertThat(domain.getClassId()).isEqualTo(CLASS_ID);
      assertThat(domain.getDaysOfWeek())
          .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
      assertThat(domain.getStartTime()).isEqualTo(LocalTime.of(10, 0));
      assertThat(domain.getCapacity()).isEqualTo(5);
      assertThat(domain.isActive()).isTrue();
    }

    @Test
    @DisplayName("ClassSlot → ClassSlotEntity 변환 시 id가 null이면 Entity id도 null이다 (신규)")
    void fromDomain_nullId_entityIdIsNull() {
      // given
      ClassSlot domain = buildDomain(null, CLASS_ID);

      // when
      ClassSlotEntity entity = ClassSlotEntity.fromDomain(domain);

      // then
      assertThat(entity.getId()).isNull();
      assertThat(entity.getClassId()).isEqualTo(CLASS_ID);
    }

    @Test
    @DisplayName("inactive 슬롯을 저장하면 active=false가 유지된다")
    void fromDomain_inactiveSlot_entityActiveIsFalse() {
      // given
      ClassSlot inactiveSlot =
          ClassSlot.builder()
              .id(SLOT_ID)
              .classId(CLASS_ID)
              .daysOfWeek(List.of(DayOfWeek.FRIDAY))
              .startTime(LocalTime.of(18, 0))
              .capacity(4)
              .active(false)
              .build();

      // when
      ClassSlotEntity entity = ClassSlotEntity.fromDomain(inactiveSlot);

      // then
      assertThat(entity.isActive()).isFalse();
    }
  }
}
