package momzzangseven.mztkbe.modules.location.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.vo.AddressData;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Location 도메인 모델 단위 테스트")
class LocationTest {

  @Nested
  @DisplayName("팩토리 메서드 테스트")
  class FactoryMethodTest {

    @Test
    @DisplayName("create() 메서드로 Location 생성 성공")
    void createLocation() {
      // given
      Long userId = 123L;
      String locationName = "서울대학교 체육관";
      String postalCode = "08826";
      String address = "서울특별시 관악구 관악로 1";
      String detailAddress = "2층 헬스장";
      GpsCoordinate coordinate = new GpsCoordinate(37.4601908, 126.9519817);
      AddressData addressData = new AddressData(address, postalCode, detailAddress);

      Instant beforeCreation = Instant.now();

      // when
      Location location = Location.create(userId, locationName, coordinate, addressData);

      Instant afterCreation = Instant.now();

      // then
      assertThat(location).isNotNull();
      assertThat(location.getId()).isNull(); // 아직 저장 전
      assertThat(location.getUserId()).isEqualTo(userId);
      assertThat(location.getLocationName()).isEqualTo(locationName);
      assertThat(location.getPostalCode()).isEqualTo(postalCode);
      assertThat(location.getAddress()).isEqualTo(address);
      assertThat(location.getDetailAddress()).isEqualTo(detailAddress);
      assertThat(location.getCoordinate()).isEqualTo(coordinate);
      assertThat(location.getRegisteredAt())
          .isAfterOrEqualTo(beforeCreation)
          .isBeforeOrEqualTo(afterCreation);
    }

    @Test
    @DisplayName("detailAddress 없이 Location 생성 가능")
    void createLocationWithoutDetailAddress() {
      // given
      Long userId = 123L;
      String locationName = "서울대학교 체육관";
      String postalCode = "08826";
      String address = "서울특별시 관악구 관악로 1";
      GpsCoordinate coordinate = new GpsCoordinate(37.4601908, 126.9519817);
      AddressData addressData = new AddressData(address, postalCode, null);

      // when
      Location location = Location.create(userId, locationName, coordinate, addressData);

      // then
      assertThat(location.getDetailAddress()).isEmpty();
    }

    @Test
    @DisplayName("registeredAt은 현재 시간으로 자동 설정됨")
    void registeredAtIsSetToCurrentTime() {
      // given
      Instant before = Instant.now();
      AddressData addressData = new AddressData("서울시 관악구", "08826", null);

      // when
      Location location =
          Location.create(123L, "서울대 체육관", new GpsCoordinate(37.46, 126.95), addressData);

      Instant after = Instant.now();

      // then
      assertThat(location.getRegisteredAt()).isBetween(before, after);
    }
  }

  @Nested
  @DisplayName("소유권 확인 테스트")
  class OwnershipTest {

    @Test
    @DisplayName("동일한 userId면 isOwnedBy true")
    void isOwnedByReturnsTrueForSameUserId() {
      // given
      Long userId = 123L;
      AddressData addressData = new AddressData("서울시 관악구", "08826", null);
      Location location =
          Location.create(userId, "서울대 체육관", new GpsCoordinate(37.46, 126.95), addressData);

      // when
      boolean isOwned = location.isOwnedBy(userId);

      // then
      assertThat(isOwned).isTrue();
    }

    @Test
    @DisplayName("다른 userId면 isOwnedBy false")
    void isOwnedByReturnsFalseForDifferentUserId() {
      // given
      Long ownerId = 123L;
      Long otherUserId = 456L;
      AddressData addressData = new AddressData("서울시 관악구", "08826", null);
      Location location =
          Location.create(ownerId, "서울대 체육관", new GpsCoordinate(37.46, 126.95), addressData);

      // when
      boolean isOwned = location.isOwnedBy(otherUserId);

      // then
      assertThat(isOwned).isFalse();
    }
  }

  @Nested
  @DisplayName("거리 계산 테스트")
  class DistanceCalculationTest {

    @Test
    @DisplayName("동일한 좌표와의 거리는 0m")
    void calculateDistanceFromSameCoordinate() {
      // given
      GpsCoordinate coordinate = new GpsCoordinate(37.5665, 126.9780);
      AddressData addressData = new AddressData("서울시 중구", "04524", null);
      Location location = Location.create(123L, "서울시청", coordinate, addressData);

      // when
      double distance = location.calculateDistanceFrom(coordinate);

      // then
      assertThat(distance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("다른 좌표와의 거리 계산")
    void calculateDistanceFromDifferentCoordinate() {
      // given
      GpsCoordinate seoulCityHall = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate gwanghwamun = new GpsCoordinate(37.5720, 126.9769);
      AddressData addressData = new AddressData("서울시 중구", "04524", null);

      Location location = Location.create(123L, "서울시청", seoulCityHall, addressData);

      // when
      double distance = location.calculateDistanceFrom(gwanghwamun);

      // then
      assertThat(distance).isBetween(600.0, 650.0); // 약 620m
    }

    @Test
    @DisplayName("5m 이내 거리 정밀 측정")
    void calculateDistanceWithin5Meters() {
      // given
      GpsCoordinate coord1 = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate coord2 = new GpsCoordinate(37.56654, 126.9780); // 약 4.5m 차이
      AddressData addressData = new AddressData("주소", "00000", null);

      Location location = Location.create(123L, "테스트", coord1, addressData);

      // when
      double distance = location.calculateDistanceFrom(coord2);

      // then
      assertThat(distance).isLessThan(5.0);
    }
  }

  @Nested
  @DisplayName("Builder 테스트")
  class BuilderTest {

    @Test
    @DisplayName("Builder로 모든 필드 설정")
    void buildLocationWithAllFields() {
      // given
      Long id = 1L;
      Long userId = 123L;
      String locationName = "서울대학교 체육관";
      String postalCode = "08826";
      String address = "서울특별시 관악구 관악로 1";
      String detailAddress = "2층 헬스장";
      GpsCoordinate coordinate = new GpsCoordinate(37.4601908, 126.9519817);
      Instant registeredAt = Instant.now();

      // when
      Location location =
          Location.builder()
              .id(id)
              .userId(userId)
              .locationName(locationName)
              .postalCode(postalCode)
              .address(address)
              .detailAddress(detailAddress)
              .coordinate(coordinate)
              .registeredAt(registeredAt)
              .build();

      // then
      assertThat(location.getId()).isEqualTo(id);
      assertThat(location.getUserId()).isEqualTo(userId);
      assertThat(location.getLocationName()).isEqualTo(locationName);
      assertThat(location.getPostalCode()).isEqualTo(postalCode);
      assertThat(location.getAddress()).isEqualTo(address);
      assertThat(location.getDetailAddress()).isEqualTo(detailAddress);
      assertThat(location.getCoordinate()).isEqualTo(coordinate);
      assertThat(location.getRegisteredAt()).isEqualTo(registeredAt);
    }
  }
}
