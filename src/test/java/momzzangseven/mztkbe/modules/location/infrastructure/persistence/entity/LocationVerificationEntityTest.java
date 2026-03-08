package momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocationVerificationEntity 단위 테스트")
class LocationVerificationEntityTest {

  @Nested
  @DisplayName("fromDomain() - Domain → Entity 변환")
  class FromDomainTest {

    @Test
    @DisplayName("Domain Model을 Entity로 변환 - 인증 성공")
    void convertFromDomainSuccess() {
      // given
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4602015, 126.9520124);
      Instant verifiedAt = Instant.parse("2026-02-04T10:30:00Z");

      LocationVerification domain =
          LocationVerification.builder()
              .id(100L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(verifiedAt)
              .build();

      // when
      LocationVerificationEntity entity = LocationVerificationEntity.fromDomain(domain);

      // then
      assertThat(entity.getId()).isEqualTo(100L);
      assertThat(entity.getUserId()).isEqualTo(123L);
      assertThat(entity.getLocationId()).isEqualTo(1L);
      assertThat(entity.getLocationName()).isEqualTo("서울대학교 체육관");
      assertThat(entity.getIsVerified()).isTrue();
      assertThat(entity.getDistance()).isEqualTo(3.47);
      assertThat(entity.getRegisteredLatitude()).isEqualTo(37.4601908);
      assertThat(entity.getRegisteredLongitude()).isEqualTo(126.9519817);
      assertThat(entity.getCurrentLatitude()).isEqualTo(37.4602015);
      assertThat(entity.getCurrentLongitude()).isEqualTo(126.9520124);
      assertThat(entity.getVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    @DisplayName("Domain Model을 Entity로 변환 - 인증 실패")
    void convertFromDomainFailure() {
      // given
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4606012, 126.9525210);

      LocationVerification domain =
          LocationVerification.builder()
              .id(101L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(false)
              .distance(47.82)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(Instant.now())
              .build();

      // when
      LocationVerificationEntity entity = LocationVerificationEntity.fromDomain(domain);

      // then
      assertThat(entity.getIsVerified()).isFalse();
      assertThat(entity.getDistance()).isEqualTo(47.82);
    }

    @Test
    @DisplayName("locationId가 null인 경우 (위치 삭제됨)")
    void convertFromDomainWithNullLocationId() {
      // given
      LocationVerification domain =
          LocationVerification.builder()
              .id(102L)
              .userId(123L)
              .locationId(null) // 위치가 삭제되어 null
              .locationName("삭제된 체육관")
              .isVerified(true)
              .distance(2.5)
              .registeredCoordinate(new GpsCoordinate(37.46, 126.95))
              .currentCoordinate(new GpsCoordinate(37.46001, 126.95001))
              .verifiedAt(Instant.now())
              .build();

      // when
      LocationVerificationEntity entity = LocationVerificationEntity.fromDomain(domain);

      // then
      assertThat(entity.getLocationId()).isNull();
      assertThat(entity.getLocationName()).isEqualTo("삭제된 체육관"); // 이름은 보존됨
    }
  }

  @Nested
  @DisplayName("toDomain() - Entity → Domain 변환")
  class ToDomainTest {

    @Test
    @DisplayName("Entity를 Domain Model로 변환")
    void convertToDomain() {
      // given
      Instant verifiedAt = Instant.parse("2026-02-04T10:30:00Z");

      LocationVerificationEntity entity =
          LocationVerificationEntity.builder()
              .id(100L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredLatitude(37.4601908)
              .registeredLongitude(126.9519817)
              .currentLatitude(37.4602015)
              .currentLongitude(126.9520124)
              .verifiedAt(verifiedAt)
              .build();

      // when
      LocationVerification domain = entity.toDomain();

      // then
      assertThat(domain.getId()).isEqualTo(100L);
      assertThat(domain.getUserId()).isEqualTo(123L);
      assertThat(domain.getLocationId()).isEqualTo(1L);
      assertThat(domain.getLocationName()).isEqualTo("서울대학교 체육관");
      assertThat(domain.isVerified()).isTrue();
      assertThat(domain.getDistance()).isEqualTo(3.47);
      assertThat(domain.getRegisteredCoordinate().latitude()).isEqualTo(37.4601908);
      assertThat(domain.getRegisteredCoordinate().longitude()).isEqualTo(126.9519817);
      assertThat(domain.getCurrentCoordinate().latitude()).isEqualTo(37.4602015);
      assertThat(domain.getCurrentCoordinate().longitude()).isEqualTo(126.9520124);
      assertThat(domain.getVerifiedAt()).isEqualTo(verifiedAt);
    }
  }

  @Nested
  @DisplayName("양방향 변환 (Bidirectional Conversion)")
  class BidirectionalConversionTest {

    @Test
    @DisplayName("Domain → Entity → Domain 변환 후 동일성 유지")
    void domainToEntityToDomain() {
      // given - 원본 Domain
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4602015, 126.9520124);
      Instant verifiedAt = Instant.parse("2026-02-04T10:30:00Z");

      LocationVerification originalDomain =
          LocationVerification.builder()
              .id(100L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(verifiedAt)
              .build();

      // when - Domain → Entity → Domain
      LocationVerificationEntity entity = LocationVerificationEntity.fromDomain(originalDomain);
      LocationVerification convertedDomain = entity.toDomain();

      // then - 모든 필드가 동일해야 함
      assertThat(convertedDomain.getId()).isEqualTo(originalDomain.getId());
      assertThat(convertedDomain.getUserId()).isEqualTo(originalDomain.getUserId());
      assertThat(convertedDomain.getLocationId()).isEqualTo(originalDomain.getLocationId());
      assertThat(convertedDomain.getLocationName()).isEqualTo(originalDomain.getLocationName());
      assertThat(convertedDomain.isVerified()).isEqualTo(originalDomain.isVerified());
      assertThat(convertedDomain.getDistance()).isEqualTo(originalDomain.getDistance());
      assertThat(convertedDomain.getRegisteredCoordinate())
          .isEqualTo(originalDomain.getRegisteredCoordinate());
      assertThat(convertedDomain.getCurrentCoordinate())
          .isEqualTo(originalDomain.getCurrentCoordinate());
      assertThat(convertedDomain.getVerifiedAt()).isEqualTo(originalDomain.getVerifiedAt());
    }

    @Test
    @DisplayName("Entity → Domain → Entity 변환 후 동일성 유지")
    void entityToDomainToEntity() {
      // given - 원본 Entity
      Instant verifiedAt = Instant.parse("2026-02-04T10:30:00Z");

      LocationVerificationEntity originalEntity =
          LocationVerificationEntity.builder()
              .id(100L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredLatitude(37.4601908)
              .registeredLongitude(126.9519817)
              .currentLatitude(37.4602015)
              .currentLongitude(126.9520124)
              .verifiedAt(verifiedAt)
              .build();

      // when - Entity → Domain → Entity
      LocationVerification domain = originalEntity.toDomain();
      LocationVerificationEntity convertedEntity = LocationVerificationEntity.fromDomain(domain);

      // then - 모든 필드가 동일해야 함
      assertThat(convertedEntity.getId()).isEqualTo(originalEntity.getId());
      assertThat(convertedEntity.getUserId()).isEqualTo(originalEntity.getUserId());
      assertThat(convertedEntity.getLocationId()).isEqualTo(originalEntity.getLocationId());
      assertThat(convertedEntity.getLocationName()).isEqualTo(originalEntity.getLocationName());
      assertThat(convertedEntity.getIsVerified()).isEqualTo(originalEntity.getIsVerified());
      assertThat(convertedEntity.getDistance()).isEqualTo(originalEntity.getDistance());
      assertThat(convertedEntity.getRegisteredLatitude())
          .isEqualTo(originalEntity.getRegisteredLatitude());
      assertThat(convertedEntity.getRegisteredLongitude())
          .isEqualTo(originalEntity.getRegisteredLongitude());
      assertThat(convertedEntity.getCurrentLatitude())
          .isEqualTo(originalEntity.getCurrentLatitude());
      assertThat(convertedEntity.getCurrentLongitude())
          .isEqualTo(originalEntity.getCurrentLongitude());
      assertThat(convertedEntity.getVerifiedAt()).isEqualTo(originalEntity.getVerifiedAt());
    }
  }
}
