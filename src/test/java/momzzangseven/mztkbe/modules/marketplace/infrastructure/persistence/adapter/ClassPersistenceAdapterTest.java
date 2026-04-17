package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.MarketplaceClassEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.MarketplaceClassJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassPersistenceAdapter 단위 테스트")
class ClassPersistenceAdapterTest {

  @Mock private MarketplaceClassJpaRepository classJpaRepository;
  @Mock private LoadClassTagPort loadClassTagPort;

  @InjectMocks private ClassPersistenceAdapter classPersistenceAdapter;

  @Captor private ArgumentCaptor<MarketplaceClassEntity> entityCaptor;

  // ============================================
  // Test Fixtures
  // ============================================

  private static final Long CLASS_ID = 10L;
  private static final Long TRAINER_ID = 1L;

  private static MarketplaceClassEntity buildEntity(Long id, Long trainerId) {
    return MarketplaceClassEntity.builder()
        .id(id)
        .trainerId(trainerId)
        .title("PT 60분 기초")
        .category(ClassCategory.PT)
        .description("기초 체력 향상을 위한 PT 클래스")
        .priceAmount(50000)
        .durationMinutes(60)
        .version(0L)
        .active(true)
        .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 1, 12, 0))
        .build();
  }

  private static MarketplaceClass buildDomain(Long id, Long trainerId) {
    return MarketplaceClass.builder()
        .id(id)
        .trainerId(trainerId)
        .title("PT 60분 기초")
        .category(ClassCategory.PT)
        .description("기초 체력 향상을 위한 PT 클래스")
        .priceAmount(50000)
        .durationMinutes(60)
        .version(0L)
        .active(true)
        .tags(List.of("다이어트"))
        .features(List.of("1:1 맞춤"))
        .build();
  }

  // ============================================
  // save() — SaveClassPort
  // ============================================

  @Nested
  @DisplayName("save() — SaveClassPort 구현")
  class SaveTests {

    @Test
    @DisplayName("도메인 모델을 Entity로 변환하여 JPA save 후 도메인 모델(태그 포함)로 반환")
    void save_convertsDomainToEntityAndReturns() {
      // given
      MarketplaceClass domain = buildDomain(null, TRAINER_ID);
      MarketplaceClassEntity savedEntity = buildEntity(CLASS_ID, TRAINER_ID);
      given(classJpaRepository.save(any(MarketplaceClassEntity.class))).willReturn(savedEntity);

      // when
      MarketplaceClass result = classPersistenceAdapter.save(domain);

      // then
      assertThat(result.getId()).isEqualTo(CLASS_ID);
      assertThat(result.getTrainerId()).isEqualTo(TRAINER_ID);
      assertThat(result.getTitle()).isEqualTo("PT 60분 기초");
      // 반환 도메인의 태그는 입력 도메인의 태그(다이어트)를 그대로 유지
      assertThat(result.getTags()).containsExactly("다이어트");
    }

    @Test
    @DisplayName("JPA repository.save()를 정확히 1번 호출한다")
    void save_callsRepositorySaveOnce() {
      // given
      MarketplaceClass domain = buildDomain(null, TRAINER_ID);
      MarketplaceClassEntity savedEntity = buildEntity(CLASS_ID, TRAINER_ID);
      given(classJpaRepository.save(any(MarketplaceClassEntity.class))).willReturn(savedEntity);

      // when
      classPersistenceAdapter.save(domain);

      // then
      then(classJpaRepository).should(times(1)).save(any(MarketplaceClassEntity.class));
    }

    @Test
    @DisplayName("Entity 변환 시 trainerId, title, active 필드가 올바르게 매핑된다")
    void save_entityHasCorrectFields() {
      // given
      MarketplaceClass domain = buildDomain(null, TRAINER_ID);
      MarketplaceClassEntity savedEntity = buildEntity(CLASS_ID, TRAINER_ID);
      given(classJpaRepository.save(entityCaptor.capture())).willReturn(savedEntity);

      // when
      classPersistenceAdapter.save(domain);

      // then
      MarketplaceClassEntity captured = entityCaptor.getValue();
      assertThat(captured.getTrainerId()).isEqualTo(TRAINER_ID);
      assertThat(captured.getTitle()).isEqualTo("PT 60분 기초");
      assertThat(captured.isActive()).isTrue();
      assertThat(captured.getCategory()).isEqualTo(ClassCategory.PT);
    }
  }

  // ============================================
  // findById() — LoadClassPort
  // ============================================

  @Nested
  @DisplayName("findById() — LoadClassPort 구현")
  class FindByIdTests {

    @Test
    @DisplayName("존재하는 classId 조회 시 태그를 합쳐 도메인 모델로 반환")
    void findById_returnsDomainWithTags_whenExists() {
      // given
      MarketplaceClassEntity entity = buildEntity(CLASS_ID, TRAINER_ID);
      given(classJpaRepository.findById(CLASS_ID)).willReturn(Optional.of(entity));
      given(loadClassTagPort.findTagNamesByClassId(CLASS_ID)).willReturn(List.of("다이어트", "근력강화"));

      // when
      Optional<MarketplaceClass> result = classPersistenceAdapter.findById(CLASS_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(CLASS_ID);
      assertThat(result.get().getTags()).containsExactly("다이어트", "근력강화");
    }

    @Test
    @DisplayName("존재하지 않는 classId 조회 시 빈 Optional 반환")
    void findById_returnsEmpty_whenNotExists() {
      // given
      given(classJpaRepository.findById(999L)).willReturn(Optional.empty());

      // when
      Optional<MarketplaceClass> result = classPersistenceAdapter.findById(999L);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("classId 조회 시 LoadClassTagPort를 1번 호출한다")
    void findById_callsTagPortOnce() {
      // given
      MarketplaceClassEntity entity = buildEntity(CLASS_ID, TRAINER_ID);
      given(classJpaRepository.findById(CLASS_ID)).willReturn(Optional.of(entity));
      given(loadClassTagPort.findTagNamesByClassId(CLASS_ID)).willReturn(List.of());

      // when
      classPersistenceAdapter.findById(CLASS_ID);

      // then
      then(loadClassTagPort).should(times(1)).findTagNamesByClassId(CLASS_ID);
    }
  }

  // ============================================
  // findActiveClasses() — LoadClassPort
  // ============================================

  @Nested
  @DisplayName("findActiveClasses() — LoadClassPort 구현")
  class FindActiveClassesTests {

    @Test
    @DisplayName("active 클래스 목록을 페이지네이션하여 ClassItem 리스트로 반환")
    void findActiveClasses_returnsMappedPage() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      MarketplaceClassEntity entity = buildEntity(CLASS_ID, TRAINER_ID);
      Page<MarketplaceClassEntity> entityPage = new PageImpl<>(List.of(entity), pageable, 1);
      given(classJpaRepository.findByActiveTrueOrderByCreatedAtDesc(pageable))
          .willReturn(entityPage);
      given(loadClassTagPort.findTagsByClassIdsIn(List.of(CLASS_ID)))
          .willReturn(Map.of(CLASS_ID, List.of("다이어트")));

      // when
      var result =
          classPersistenceAdapter.findActiveClasses(
              null, null, null, "RATING", null, null, null, pageable);

      // then
      assertThat(result.getTotalElements()).isEqualTo(1);
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).classId()).isEqualTo(CLASS_ID);
      assertThat(result.getContent().get(0).tags()).containsExactly("다이어트");
    }

    @Test
    @DisplayName("active 클래스가 없을 때 빈 페이지 반환")
    void findActiveClasses_returnsEmptyPage_whenNoClasses() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      Page<MarketplaceClassEntity> emptyPage = Page.empty(pageable);
      given(classJpaRepository.findByActiveTrueOrderByCreatedAtDesc(pageable))
          .willReturn(emptyPage);

      // when
      var result =
          classPersistenceAdapter.findActiveClasses(
              null, null, null, "RATING", null, null, null, pageable);

      // then
      assertThat(result.getTotalElements()).isZero();
      assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("클래스 목록이 비어있으면 LoadClassTagPort를 호출하지 않는다")
    void findActiveClasses_doesNotCallTagPort_whenNoClassIds() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      Page<MarketplaceClassEntity> emptyPage = Page.empty(pageable);
      given(classJpaRepository.findByActiveTrueOrderByCreatedAtDesc(pageable))
          .willReturn(emptyPage);

      // when
      classPersistenceAdapter.findActiveClasses(
          null, null, null, "RATING", null, null, null, pageable);

      // then
      then(loadClassTagPort).should(times(0)).findTagsByClassIdsIn(anyList());
    }
  }

  // ============================================
  // findByTrainerId() — LoadClassPort
  // ============================================

  @Nested
  @DisplayName("findByTrainerId() — LoadClassPort 구현")
  class FindByTrainerIdTests {

    @Test
    @DisplayName("trainerId로 클래스 목록 조회 시 도메인 모델(태그 포함)로 반환")
    void findByTrainerId_returnsDomainPage() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      MarketplaceClassEntity entity = buildEntity(CLASS_ID, TRAINER_ID);
      Page<MarketplaceClassEntity> entityPage = new PageImpl<>(List.of(entity), pageable, 1);
      given(classJpaRepository.findByTrainerIdOrderByCreatedAtDesc(TRAINER_ID, pageable))
          .willReturn(entityPage);
      given(loadClassTagPort.findTagsByClassIdsIn(List.of(CLASS_ID)))
          .willReturn(Map.of(CLASS_ID, List.of("체중감량")));

      // when
      Page<MarketplaceClass> result = classPersistenceAdapter.findByTrainerId(TRAINER_ID, pageable);

      // then
      assertThat(result.getTotalElements()).isEqualTo(1);
      assertThat(result.getContent().get(0).getId()).isEqualTo(CLASS_ID);
      assertThat(result.getContent().get(0).getTags()).containsExactly("체중감량");
    }

    @Test
    @DisplayName("trainerId에 해당하는 클래스가 없을 때 빈 페이지 반환")
    void findByTrainerId_returnsEmptyPage_whenNoClasses() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      Page<MarketplaceClassEntity> emptyPage = Page.empty(pageable);
      given(classJpaRepository.findByTrainerIdOrderByCreatedAtDesc(TRAINER_ID, pageable))
          .willReturn(emptyPage);

      // when
      Page<MarketplaceClass> result = classPersistenceAdapter.findByTrainerId(TRAINER_ID, pageable);

      // then
      assertThat(result.getTotalElements()).isZero();
    }
  }

  // ============================================
  // Entity ↔ Domain 변환 검증
  // ============================================

  @Nested
  @DisplayName("Entity ↔ Domain 변환 검증")
  class MappingTests {

    @Test
    @DisplayName("Entity → Domain 변환 시 모든 기본 필드가 올바르게 매핑된다")
    void toDomainWithTags_mapsAllFields() {
      // given
      MarketplaceClassEntity entity = buildEntity(CLASS_ID, TRAINER_ID);
      List<String> tags = List.of("다이어트");

      // when
      MarketplaceClass domain = entity.toDomainWithTags(tags);

      // then
      assertThat(domain.getId()).isEqualTo(CLASS_ID);
      assertThat(domain.getTrainerId()).isEqualTo(TRAINER_ID);
      assertThat(domain.getTitle()).isEqualTo("PT 60분 기초");
      assertThat(domain.getCategory()).isEqualTo(ClassCategory.PT);
      assertThat(domain.getPriceAmount()).isEqualTo(50000);
      assertThat(domain.getDurationMinutes()).isEqualTo(60);
      assertThat(domain.isActive()).isTrue();
      assertThat(domain.getTags()).containsExactly("다이어트");
    }

    @Test
    @DisplayName("Domain → Entity 변환 시 id가 null이면 Entity id도 null이다 (신규 저장)")
    void fromDomain_nullId_entityIdIsNull() {
      // given
      MarketplaceClass domain = buildDomain(null, TRAINER_ID);

      // when
      MarketplaceClassEntity entity = MarketplaceClassEntity.fromDomain(domain);

      // then
      assertThat(entity.getId()).isNull();
      assertThat(entity.getTrainerId()).isEqualTo(TRAINER_ID);
    }

    @Test
    @DisplayName("features가 pipe-delimited로 인코딩/디코딩된다")
    void features_encodedAndDecodedCorrectly() {
      // given
      MarketplaceClass domain =
          MarketplaceClass.builder()
              .id(CLASS_ID)
              .trainerId(TRAINER_ID)
              .title("제목")
              .category(ClassCategory.YOGA)
              .description("설명")
              .priceAmount(30000)
              .durationMinutes(60)
              .version(0L)
              .active(true)
              .features(List.of("유연성 향상", "코어 강화"))
              .build();

      // when
      MarketplaceClassEntity entity = MarketplaceClassEntity.fromDomain(domain);

      // then: Entity의 decodeFeatures()가 원본 list를 반환해야 함
      assertThat(entity.decodeFeatures()).containsExactly("유연성 향상", "코어 강화");
    }

    @Test
    @DisplayName("features가 null/빈 리스트이면 Entity는 null로 인코딩하고 디코딩 시 빈 리스트 반환")
    void features_nullList_encodedAsNull_decodedAsEmpty() {
      // given
      MarketplaceClass domain =
          MarketplaceClass.builder()
              .id(CLASS_ID)
              .trainerId(TRAINER_ID)
              .title("제목")
              .category(ClassCategory.PT)
              .description("설명")
              .priceAmount(30000)
              .durationMinutes(60)
              .version(0L)
              .active(true)
              .features(List.of())
              .build();

      // when
      MarketplaceClassEntity entity = MarketplaceClassEntity.fromDomain(domain);

      // then
      assertThat(entity.decodeFeatures()).isEmpty();
    }
  }
}
