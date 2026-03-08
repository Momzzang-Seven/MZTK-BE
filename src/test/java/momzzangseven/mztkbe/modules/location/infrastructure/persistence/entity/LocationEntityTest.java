package momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocationEntity 단위 테스트")
class LocationEntityTest {

  @Nested
  @DisplayName("from() - Domain → Entity 변환")
  class FromDomainTest {

    @Test
    @DisplayName("Location 도메인 모델을 Entity로 변환")
    void convertDomainToEntity() {
      // given
      GpsCoordinate coordinate = new GpsCoordinate(37.4601908, 126.9519817);
      Instant now = Instant.now();

      Location location =
          Location.builder()
              .id(1L)
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress("2층 헬스장")
              .coordinate(coordinate)
              .registeredAt(now)
              .build();

      // when
      LocationEntity entity = LocationEntity.from(location);

      // then
      assertThat(entity.getId()).isEqualTo(1L);
      assertThat(entity.getUserId()).isEqualTo(123L);
      assertThat(entity.getLocationName()).isEqualTo("서울대학교 체육관");
      assertThat(entity.getPostalCode()).isEqualTo("08826");
      assertThat(entity.getAddress()).isEqualTo("서울특별시 관악구 관악로 1");
      assertThat(entity.getDetailAddress()).isEqualTo("2층 헬스장");
      assertThat(entity.getLatitude()).isEqualTo(37.4601908);
      assertThat(entity.getLongitude()).isEqualTo(126.9519817);
      assertThat(entity.getRegisteredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("detailAddress가 null인 경우도 변환 가능")
    void convertDomainWithNullDetailAddress() {
      // given
      Location location =
          Location.builder()
              .id(1L)
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress(null)
              .coordinate(new GpsCoordinate(37.46, 126.95))
              .registeredAt(Instant.now())
              .build();

      // when
      LocationEntity entity = LocationEntity.from(location);

      // then
      assertThat(entity.getDetailAddress()).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain() - Entity → Domain 변환")
  class ToDomainTest {

    @Test
    @DisplayName("Entity를 Location 도메인 모델로 변환")
    void convertEntityToDomain() {
      // given
      Instant now = Instant.now();

      LocationEntity entity =
          LocationEntity.builder()
              .id(1L)
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress("2층 헬스장")
              .latitude(37.4601908)
              .longitude(126.9519817)
              .registeredAt(now)
              .updatedAt(now)
              .build();

      // when
      Location location = entity.toDomain();

      // then
      assertThat(location.getId()).isEqualTo(1L);
      assertThat(location.getUserId()).isEqualTo(123L);
      assertThat(location.getLocationName()).isEqualTo("서울대학교 체육관");
      assertThat(location.getPostalCode()).isEqualTo("08826");
      assertThat(location.getAddress()).isEqualTo("서울특별시 관악구 관악로 1");
      assertThat(location.getDetailAddress()).isEqualTo("2층 헬스장");
      assertThat(location.getCoordinate().latitude()).isEqualTo(37.4601908);
      assertThat(location.getCoordinate().longitude()).isEqualTo(126.9519817);
      assertThat(location.getRegisteredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("GpsCoordinate 값 객체가 올바르게 생성됨")
    void createsGpsCoordinateValueObject() {
      // given
      LocationEntity entity =
          LocationEntity.builder()
              .id(1L)
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address("주소")
              .detailAddress(null)
              .latitude(37.5)
              .longitude(127.0)
              .registeredAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      // when
      Location location = entity.toDomain();

      // then
      assertThat(location.getCoordinate()).isNotNull();
      assertThat(location.getCoordinate()).isInstanceOf(GpsCoordinate.class);
      assertThat(location.getCoordinate().latitude()).isEqualTo(37.5);
      assertThat(location.getCoordinate().longitude()).isEqualTo(127.0);
    }
  }

  @Nested
  @DisplayName("양방향 변환 테스트")
  class BidirectionalConversionTest {

    @Test
    @DisplayName("Domain → Entity → Domain 변환 시 데이터 보존")
    void domainToEntityToDomainPreservesData() {
      // given
      Location originalLocation =
          Location.builder()
              .id(1L)
              .userId(123L)
              .locationName("서울대학교 체육관")
              .postalCode("08826")
              .address("서울특별시 관악구 관악로 1")
              .detailAddress("2층 헬스장")
              .coordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .registeredAt(Instant.now())
              .build();

      // when
      LocationEntity entity = LocationEntity.from(originalLocation);
      Location convertedLocation = entity.toDomain();

      // then
      assertThat(convertedLocation.getId()).isEqualTo(originalLocation.getId());
      assertThat(convertedLocation.getUserId()).isEqualTo(originalLocation.getUserId());
      assertThat(convertedLocation.getLocationName()).isEqualTo(originalLocation.getLocationName());
      assertThat(convertedLocation.getPostalCode()).isEqualTo(originalLocation.getPostalCode());
      assertThat(convertedLocation.getAddress()).isEqualTo(originalLocation.getAddress());
      assertThat(convertedLocation.getDetailAddress())
          .isEqualTo(originalLocation.getDetailAddress());
      assertThat(convertedLocation.getCoordinate()).isEqualTo(originalLocation.getCoordinate());
      assertThat(convertedLocation.getRegisteredAt()).isEqualTo(originalLocation.getRegisteredAt());
    }
  }
}
