package momzzangseven.mztkbe.modules.location.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import momzzangseven.mztkbe.modules.location.domain.vo.VerificationRadius;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocationVerification 도메인 모델 단위 테스트")
class LocationVerificationTest {

  @Nested
  @DisplayName("create() Factory Method")
  class CreateFactoryMethodTest {

    @Test
    @DisplayName("인증 성공 - 반경 내 (5m 이내)")
    void createVerificationSuccess() {
      // given
      Long userId = 123L;
      Location location = createMockLocation(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4602015, 126.9520124);
      // 실제 거리: 약 3~4m

      VerificationRadius radius = createMockRadius(5.0);

      // when
      LocationVerification verification =
          LocationVerification.create(userId, location, currentCoordinate, radius);

      // then
      assertThat(verification.getUserId()).isEqualTo(userId);
      assertThat(verification.getLocationId()).isEqualTo(location.getId());
      assertThat(verification.getLocationName()).isEqualTo(location.getLocationName());
      assertThat(verification.isVerified()).isTrue();
      assertThat(verification.getDistance()).isLessThan(5.0);
      assertThat(verification.getRegisteredCoordinate()).isEqualTo(location.getCoordinate());
      assertThat(verification.getCurrentCoordinate()).isEqualTo(currentCoordinate);
      assertThat(verification.getVerifiedAt()).isNotNull();
      assertThat(verification.getVerifiedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("인증 실패 - 반경 초과 (5m 초과)")
    void createVerificationFailure() {
      // given
      Long userId = 123L;
      Location location = createMockLocation(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4606012, 126.9525210);
      // 실제 거리: 약 40~50m

      VerificationRadius radius = createMockRadius(5.0);

      // when
      LocationVerification verification =
          LocationVerification.create(userId, location, currentCoordinate, radius);

      // then
      assertThat(verification.isVerified()).isFalse();
      assertThat(verification.getDistance()).isGreaterThan(5.0);
      assertThat(verification.getUserId()).isEqualTo(userId);
      assertThat(verification.getLocationId()).isEqualTo(location.getId());
    }

    @Test
    @DisplayName("경계값 테스트 - 정확히 5m")
    void createVerificationAtBoundary() {
      // given
      Long userId = 123L;
      // 서울시청 좌표
      Location location = createMockLocation(37.5665, 126.9780);

      // 정확히 5m 떨어진 좌표 계산 (북쪽으로 5m)
      // 위도 1도 ≈ 111,000m
      // 5m = 5 / 111000 ≈ 0.000045도
      double targetLatitude = 37.5665 + 0.000045;
      GpsCoordinate currentCoordinate = new GpsCoordinate(targetLatitude, 126.9780);

      VerificationRadius radius = createMockRadius(5.0);

      // when
      LocationVerification verification =
          LocationVerification.create(userId, location, currentCoordinate, radius);

      // then
      double distance = verification.getDistance();
      assertThat(distance).isBetween(4.0, 6.0); // Haversine 공식 오차 범위 허용
      // 거리가 반경 내에 있으면 통과
      assertThat(verification.isVerified()).isEqualTo(distance <= 5.0);
    }

    @Test
    @DisplayName("다른 반경 설정으로 결과 달라짐")
    void createVerificationWithDifferentRadius() {
      // given
      Long userId = 123L;
      Location location = createMockLocation(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4603, 126.9522);
      // 실제 거리: 약 30m

      VerificationRadius radius5m = createMockRadius(5.0);
      VerificationRadius radius50m = createMockRadius(50.0);

      // when
      LocationVerification verification5m =
          LocationVerification.create(userId, location, currentCoordinate, radius5m);
      LocationVerification verification50m =
          LocationVerification.create(userId, location, currentCoordinate, radius50m);

      // then
      assertThat(verification5m.isVerified()).isFalse(); // 5m 초과
      assertThat(verification50m.isVerified()).isTrue(); // 50m 내
      assertThat(verification5m.getDistance()).isEqualTo(verification50m.getDistance()); // 거리는 동일
    }

    @Test
    @DisplayName("거리 0m - 정확히 같은 위치")
    void createVerificationAtSameLocation() {
      // given
      Long userId = 123L;
      GpsCoordinate sameCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      Location location = createMockLocation(37.4601908, 126.9519817);

      VerificationRadius radius = createMockRadius(5.0);

      // when
      LocationVerification verification =
          LocationVerification.create(userId, location, sameCoordinate, radius);

      // then
      assertThat(verification.getDistance()).isEqualTo(0.0);
      assertThat(verification.isVerified()).isTrue();
    }

    @Test
    @DisplayName("위치명 비정규화 확인")
    void locationNameDenormalized() {
      // given
      Long userId = 123L;
      Location location = createMockLocation(37.46, 126.95);
      location =
          Location.builder()
              .id(1L)
              .userId(userId)
              .locationName("한양대학교 체육관") // 특정 이름
              .coordinate(new GpsCoordinate(37.46, 126.95))
              .postalCode("15588")
              .address("경기도 안산시 상록구")
              .detailAddress("")
              .registeredAt(Instant.now())
              .build();

      GpsCoordinate currentCoordinate = new GpsCoordinate(37.46, 126.95);
      VerificationRadius radius = createMockRadius(5.0);

      // when
      LocationVerification verification =
          LocationVerification.create(userId, location, currentCoordinate, radius);

      // then - 위치명이 비정규화되어 저장됨
      assertThat(verification.getLocationName()).isEqualTo("한양대학교 체육관");
    }
  }

  @Nested
  @DisplayName("비즈니스 로직")
  class BusinessLogicTest {

    @Test
    @DisplayName("isSuccessful() - 인증 성공")
    void isSuccessful_whenVerified() {
      // given
      LocationVerification verification =
          LocationVerification.builder()
              .id(1L)
              .userId(123L)
              .locationId(1L)
              .locationName("테스트 체육관")
              .isVerified(true)
              .distance(3.5)
              .registeredCoordinate(new GpsCoordinate(37.46, 126.95))
              .currentCoordinate(new GpsCoordinate(37.46001, 126.95001))
              .verifiedAt(Instant.now())
              .build();

      // when & then
      assertThat(verification.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("isSuccessful() - 인증 실패")
    void isSuccessful_whenNotVerified() {
      // given
      LocationVerification verification =
          LocationVerification.builder()
              .id(1L)
              .userId(123L)
              .locationId(1L)
              .locationName("테스트 체육관")
              .isVerified(false)
              .distance(47.8)
              .registeredCoordinate(new GpsCoordinate(37.46, 126.95))
              .currentCoordinate(new GpsCoordinate(37.461, 126.951))
              .verifiedAt(Instant.now())
              .build();

      // when & then
      assertThat(verification.isSuccessful()).isFalse();
    }
  }

  @Nested
  @DisplayName("Builder 테스트")
  class BuilderTest {

    @Test
    @DisplayName("Builder로 모든 필드 설정")
    void buildWithAllFields() {
      // given
      Long id = 100L;
      Long userId = 123L;
      Long locationId = 1L;
      String locationName = "서울대학교 체육관";
      boolean isVerified = true;
      double distance = 3.47;
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4602015, 126.9520124);
      Instant verifiedAt = Instant.now();

      // when
      LocationVerification verification =
          LocationVerification.builder()
              .id(id)
              .userId(userId)
              .locationId(locationId)
              .locationName(locationName)
              .isVerified(isVerified)
              .distance(distance)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(verifiedAt)
              .build();

      // then
      assertThat(verification.getId()).isEqualTo(id);
      assertThat(verification.getUserId()).isEqualTo(userId);
      assertThat(verification.getLocationId()).isEqualTo(locationId);
      assertThat(verification.getLocationName()).isEqualTo(locationName);
      assertThat(verification.isVerified()).isEqualTo(isVerified);
      assertThat(verification.getDistance()).isEqualTo(distance);
      assertThat(verification.getRegisteredCoordinate()).isEqualTo(registeredCoordinate);
      assertThat(verification.getCurrentCoordinate()).isEqualTo(currentCoordinate);
      assertThat(verification.getVerifiedAt()).isEqualTo(verifiedAt);
    }
  }

  // Test Helpers
  private static Location createMockLocation(double latitude, double longitude) {
    return Location.builder()
        .id(1L)
        .userId(123L)
        .locationName("테스트 체육관")
        .postalCode("12345")
        .address("서울특별시 강남구")
        .detailAddress("1층")
        .coordinate(new GpsCoordinate(latitude, longitude))
        .registeredAt(Instant.now())
        .build();
  }

  private static VerificationRadius createMockRadius(double radiusMeters) {
    VerificationRadius radius = new VerificationRadius();
    radius.setRadiusMeters(radiusMeters);
    return radius;
  }
}
