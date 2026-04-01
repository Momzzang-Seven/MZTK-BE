package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.TrainerStoreEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.TrainerStoreJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorePersistenceAdapter 단위 테스트")
class StorePersistenceAdapterTest {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  @Mock private TrainerStoreJpaRepository trainerStoreJpaRepository;

  @InjectMocks private StorePersistenceAdapter storagePersistenceAdapter;

  // ============================================
  // Test Fixtures
  // ============================================

  private static TrainerStoreEntity createStoreEntity() {
    Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(127.0276, 37.4979));
    point.setSRID(4326);

    return TrainerStoreEntity.builder()
        .id(100L)
        .trainerId(1L)
        .storeName("PT Studio")
        .address("서울시 강남구")
        .detailAddress("2층")
        .location(point)
        .phoneNumber("010-1234-5678")
        .homepageUrl("https://example.com")
        .instagramUrl("https://instagram.com/test")
        .xUrl("https://x.com/test")
        .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 1, 12, 0))
        .build();
  }

  // ============================================
  // findByTrainerId() — LoadStorePort
  // ============================================

  @Nested
  @DisplayName("findByTrainerId() - LoadStorePort 구현")
  class FindByTrainerIdTests {

    @Test
    @DisplayName("스토어가 존재하면 도메인 모델로 변환하여 반환한다")
    void findByTrainerId_returnsDomainModel_whenExists() {
      // given
      Long trainerId = 1L;
      TrainerStoreEntity entity = createStoreEntity();
      given(trainerStoreJpaRepository.findByTrainerId(trainerId)).willReturn(Optional.of(entity));

      // when
      Optional<TrainerStore> result = storagePersistenceAdapter.findByTrainerId(trainerId);

      // then
      assertThat(result).isPresent();
      TrainerStore store = result.get();
      assertThat(store.getId()).isEqualTo(100L);
      assertThat(store.getTrainerId()).isEqualTo(1L);
      assertThat(store.getStoreName()).isEqualTo("PT Studio");
      assertThat(store.getAddress()).isEqualTo("서울시 강남구");
      assertThat(store.getLatitude()).isEqualTo(37.4979);
      assertThat(store.getLongitude()).isEqualTo(127.0276);
    }

    @Test
    @DisplayName("스토어가 존재하지 않으면 빈 Optional을 반환한다")
    void findByTrainerId_returnsEmpty_whenNotExists() {
      // given
      Long trainerId = 999L;
      given(trainerStoreJpaRepository.findByTrainerId(trainerId)).willReturn(Optional.empty());

      // when
      Optional<TrainerStore> result = storagePersistenceAdapter.findByTrainerId(trainerId);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JpaRepository를 정확히 1번 호출한다")
    void findByTrainerId_callsRepositoryOnce() {
      // given
      Long trainerId = 1L;
      given(trainerStoreJpaRepository.findByTrainerId(trainerId)).willReturn(Optional.empty());

      // when
      storagePersistenceAdapter.findByTrainerId(trainerId);

      // then
      then(trainerStoreJpaRepository).should(times(1)).findByTrainerId(trainerId);
    }
  }

  // ============================================
  // Entity ↔ Domain 변환 검증
  // ============================================

  @Nested
  @DisplayName("Entity ↔ Domain 변환 검증")
  class MappingTests {

    @Test
    @DisplayName("Entity → Domain 변환 시 모든 필드가 올바르게 매핑된다")
    void toDomain_mapsAllFields() {
      // given
      TrainerStoreEntity entity = createStoreEntity();

      // when
      TrainerStore domain = entity.toDomain();

      // then
      assertThat(domain.getId()).isEqualTo(entity.getId());
      assertThat(domain.getTrainerId()).isEqualTo(entity.getTrainerId());
      assertThat(domain.getStoreName()).isEqualTo(entity.getStoreName());
      assertThat(domain.getAddress()).isEqualTo(entity.getAddress());
      assertThat(domain.getDetailAddress()).isEqualTo(entity.getDetailAddress());
      assertThat(domain.getLatitude()).isEqualTo(entity.getLatitude());
      assertThat(domain.getLongitude()).isEqualTo(entity.getLongitude());
      assertThat(domain.getPhoneNumber()).isEqualTo(entity.getPhoneNumber());
      assertThat(domain.getHomepageUrl()).isEqualTo(entity.getHomepageUrl());
      assertThat(domain.getInstagramUrl()).isEqualTo(entity.getInstagramUrl());
      assertThat(domain.getXUrl()).isEqualTo(entity.getXUrl());
      assertThat(domain.getCreatedAt()).isEqualTo(entity.getCreatedAt());
      assertThat(domain.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Domain → Entity 변환 시 Point 좌표가 올바르게 생성된다")
    void fromDomain_createsPointCorrectly() {
      // given
      TrainerStore domain =
          TrainerStore.builder()
              .trainerId(1L)
              .storeName("PT Studio")
              .address("서울시 강남구")
              .latitude(37.4979)
              .longitude(127.0276)
              .build();

      // when
      TrainerStoreEntity entity = TrainerStoreEntity.fromDomain(domain);

      // then
      assertThat(entity.getLocation()).isNotNull();
      assertThat(entity.getLocation().getX()).isEqualTo(127.0276);
      assertThat(entity.getLocation().getY()).isEqualTo(37.4979);
      assertThat(entity.getLocation().getSRID()).isEqualTo(4326);
    }

    @Test
    @DisplayName("Domain → Entity 변환 시 좌표가 null이면 location도 null이다")
    void fromDomain_nullLocation_whenCoordinatesNull() {
      // given
      TrainerStore domain =
          TrainerStore.builder()
              .trainerId(1L)
              .storeName("PT Studio")
              .address("서울시 강남구")
              .latitude(null)
              .longitude(null)
              .build();

      // when
      TrainerStoreEntity entity = TrainerStoreEntity.fromDomain(domain);

      // then
      assertThat(entity.getLocation()).isNull();
      assertThat(entity.getLatitude()).isNull();
      assertThat(entity.getLongitude()).isNull();
    }
  }
}
