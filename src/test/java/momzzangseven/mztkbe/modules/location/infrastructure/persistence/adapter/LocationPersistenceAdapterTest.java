package momzzangseven.mztkbe.modules.location.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationEntity;
import momzzangseven.mztkbe.modules.location.infrastructure.repository.LocationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationPersistenceAdapter 단위 테스트")
class LocationPersistenceAdapterTest {

  @Mock private LocationJpaRepository locationJpaRepository;

  @InjectMocks private LocationPersistenceAdapter locationPersistenceAdapter;

  @Nested
  @DisplayName("save() - SaveLocationPort 구현")
  class SaveTest {

    @Test
    @DisplayName("Location 도메인 모델을 저장하고 ID가 생성된 결과 반환")
    void saveLocationAndReturnWithGeneratedId() {
      // given
      Location location =
          Location.builder()
              .id(null) // 저장 전이므로 ID 없음
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress("2층 헬스장")
              .coordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .registeredAt(Instant.now())
              .build();

      LocationEntity savedEntity =
          LocationEntity.builder()
              .id(1L) // DB에서 생성된 ID
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress("2층 헬스장")
              .latitude(37.4601908)
              .longitude(126.9519817)
              .registeredAt(location.getRegisteredAt())
              .updatedAt(location.getRegisteredAt())
              .build();

      given(locationJpaRepository.save(any(LocationEntity.class))).willReturn(savedEntity);

      // when
      Location result = locationPersistenceAdapter.save(location);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L); // ID 생성됨
      assertThat(result.getUserId()).isEqualTo(123L);
      assertThat(result.getLocationName()).isEqualTo("서울대학교 체육관");

      verify(locationJpaRepository, times(1)).save(any(LocationEntity.class));
    }

    @Test
    @DisplayName("저장 시 도메인 → 엔티티 → 도메인 변환 수행")
    void savePerformsDomainToEntityConversion() {
      // given
      GpsCoordinate coordinate = new GpsCoordinate(37.5, 127.0);
      Location location =
          Location.builder()
              .userId(999L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .coordinate(coordinate)
              .registeredAt(Instant.now())
              .build();

      LocationEntity savedEntity =
          LocationEntity.builder()
              .id(100L)
              .userId(999L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(37.5)
              .longitude(127.0)
              .registeredAt(location.getRegisteredAt())
              .updatedAt(Instant.now())
              .build();

      given(locationJpaRepository.save(any(LocationEntity.class))).willReturn(savedEntity);

      // when
      Location result = locationPersistenceAdapter.save(location);

      // then
      assertThat(result.getCoordinate()).isEqualTo(coordinate);
      assertThat(result.getCoordinate().latitude()).isEqualTo(37.5);
      assertThat(result.getCoordinate().longitude()).isEqualTo(127.0);
    }
  }

  @Nested
  @DisplayName("findByLocationId() - LoadLocationPort 구현")
  class FindByLocationIdTest {

    @Test
    @DisplayName("ID로 Location 조회 성공")
    void findLocationByIdReturnsLocation() {
      // given
      Long locationId = 1L;
      LocationEntity entity =
          LocationEntity.builder()
              .id(locationId)
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress("2층 헬스장")
              .latitude(37.4601908)
              .longitude(126.9519817)
              .registeredAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      given(locationJpaRepository.findById(locationId)).willReturn(Optional.of(entity));

      // when
      Optional<Location> result = locationPersistenceAdapter.findByLocationId(locationId);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(locationId);
      assertThat(result.get().getLocationName()).isEqualTo("서울대학교 체육관");

      verify(locationJpaRepository, times(1)).findById(locationId);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 Optional.empty() 반환")
    void findLocationByNonExistentIdReturnsEmpty() {
      // given
      Long locationId = 999L;
      given(locationJpaRepository.findById(locationId)).willReturn(Optional.empty());

      // when
      Optional<Location> result = locationPersistenceAdapter.findByLocationId(locationId);

      // then
      assertThat(result).isEmpty();
      verify(locationJpaRepository, times(1)).findById(locationId);
    }
  }

  @Nested
  @DisplayName("findByUserId() - LoadLocationPort 구현")
  class FindByUserIdTest {

    @Test
    @DisplayName("사용자 ID로 Location 목록 조회")
    void findLocationsByUserIdReturnsList() {
      // given
      Long userId = 123L;
      Instant now = Instant.now();

      LocationEntity entity1 =
          LocationEntity.builder()
              .id(1L)
              .userId(userId)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress(null)
              .latitude(37.46)
              .longitude(126.95)
              .registeredAt(now.minusSeconds(100))
              .updatedAt(now)
              .build();

      LocationEntity entity2 =
          LocationEntity.builder()
              .id(2L)
              .userId(userId)
              .locationName("홍대 헬스장")
              .postalCode("04000")
              .address("서울시 마포구")
              .detailAddress("3층")
              .latitude(37.55)
              .longitude(126.92)
              .registeredAt(now)
              .updatedAt(now)
              .build();

      given(locationJpaRepository.findByUserIdOrderByRegisteredAtDesc(userId))
          .willReturn(List.of(entity2, entity1)); // 최신 순

      // when
      List<Location> results = locationPersistenceAdapter.findByUserId(userId);

      // then
      assertThat(results).hasSize(2);
      assertThat(results.get(0).getLocationName()).isEqualTo("홍대 헬스장"); // 최신
      assertThat(results.get(1).getLocationName()).isEqualTo("서울대학교 체육관");

      verify(locationJpaRepository, times(1)).findByUserIdOrderByRegisteredAtDesc(userId);
    }

    @Test
    @DisplayName("등록된 Location이 없으면 빈 리스트 반환")
    void findLocationsByUserIdWithNoResultsReturnsEmptyList() {
      // given
      Long userId = 999L;
      given(locationJpaRepository.findByUserIdOrderByRegisteredAtDesc(userId))
          .willReturn(List.of());

      // when
      List<Location> results = locationPersistenceAdapter.findByUserId(userId);

      // then
      assertThat(results).isEmpty();
      verify(locationJpaRepository, times(1)).findByUserIdOrderByRegisteredAtDesc(userId);
    }

    @Test
    @DisplayName("여러 Location이 등록 시간 역순으로 정렬되어 반환됨")
    void findLocationsByUserIdReturnsSortedByRegisteredAtDesc() {
      // given
      Long userId = 123L;
      Instant time1 = Instant.parse("2024-01-01T00:00:00Z");
      Instant time2 = Instant.parse("2024-01-02T00:00:00Z");
      Instant time3 = Instant.parse("2024-01-03T00:00:00Z");

      LocationEntity entity1 =
          LocationEntity.builder()
              .id(1L)
              .userId(userId)
              .locationName("Location 1")
              .postalCode("00000")
              .address("주소 1")
              .detailAddress(null)
              .latitude(37.0)
              .longitude(127.0)
              .registeredAt(time1)
              .updatedAt(time1)
              .build();

      LocationEntity entity2 =
          LocationEntity.builder()
              .id(2L)
              .userId(userId)
              .locationName("Location 2")
              .postalCode("00000")
              .address("주소 2")
              .detailAddress(null)
              .latitude(37.0)
              .longitude(127.0)
              .registeredAt(time2)
              .updatedAt(time2)
              .build();

      LocationEntity entity3 =
          LocationEntity.builder()
              .id(3L)
              .userId(userId)
              .locationName("Location 3")
              .postalCode("00000")
              .address("주소 3")
              .detailAddress(null)
              .latitude(37.0)
              .longitude(127.0)
              .registeredAt(time3)
              .updatedAt(time3)
              .build();

      given(locationJpaRepository.findByUserIdOrderByRegisteredAtDesc(userId))
          .willReturn(List.of(entity3, entity2, entity1)); // 최신 순

      // when
      List<Location> results = locationPersistenceAdapter.findByUserId(userId);

      // then
      assertThat(results).hasSize(3);
      assertThat(results.get(0).getRegisteredAt()).isEqualTo(time3); // 최신
      assertThat(results.get(1).getRegisteredAt()).isEqualTo(time2);
      assertThat(results.get(2).getRegisteredAt()).isEqualTo(time1); // 가장 오래됨
    }
  }
}
