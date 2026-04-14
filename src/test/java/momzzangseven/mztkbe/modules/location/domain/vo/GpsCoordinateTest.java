package momzzangseven.mztkbe.modules.location.domain.vo;

import static org.assertj.core.api.Assertions.*;

import momzzangseven.mztkbe.global.error.location.InvalidGpsCoordinateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("GpsCoordinate 단위 테스트")
class GpsCoordinateTest {

  @Nested
  @DisplayName("생성자 테스트")
  class ConstructorTest {

    @Test
    @DisplayName("유효한 좌표로 생성 성공")
    void createWithValidCoordinates() {
      // given
      double latitude = 37.5665;
      double longitude = 126.9780;

      // when
      GpsCoordinate coordinate = new GpsCoordinate(latitude, longitude);

      // then
      assertThat(coordinate.latitude()).isEqualTo(latitude);
      assertThat(coordinate.longitude()).isEqualTo(longitude);
    }

    @Test
    @DisplayName("위도 경계값: 90도 (성공)")
    void createWithLatitude90() {
      // when & then
      assertThatCode(() -> new GpsCoordinate(90.0, 126.9780)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("위도 경계값: -90도 (성공)")
    void createWithLatitudeMinus90() {
      // when & then
      assertThatCode(() -> new GpsCoordinate(-90.0, 126.9780)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경도 경계값: 180도 (성공)")
    void createWithLongitude180() {
      // when & then
      assertThatCode(() -> new GpsCoordinate(37.5665, 180.0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경도 경계값: -180도 (성공)")
    void createWithLongitudeMinus180() {
      // when & then
      assertThatCode(() -> new GpsCoordinate(37.5665, -180.0)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.1, -91.0, -100.0, 90.1, 91.0, 100.0})
    @DisplayName("위도 범위 초과 시 예외 발생")
    void throwExceptionWhenLatitudeOutOfRange(double invalidLatitude) {
      // when & then
      assertThatThrownBy(() -> new GpsCoordinate(invalidLatitude, 126.9780))
          .isInstanceOf(InvalidGpsCoordinateException.class)
          .hasMessageContaining("Latitude must be between -90 and 90");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.1, -181.0, -200.0, 180.1, 181.0, 200.0})
    @DisplayName("경도 범위 초과 시 예외 발생")
    void throwExceptionWhenLongitudeOutOfRange(double invalidLongitude) {
      // when & then
      assertThatThrownBy(() -> new GpsCoordinate(37.5665, invalidLongitude))
          .isInstanceOf(InvalidGpsCoordinateException.class)
          .hasMessageContaining("Longitude must be between -180 and 180");
    }
  }

  @Nested
  @DisplayName("거리 계산 테스트 (Haversine formula)")
  class DistanceCalculationTest {

    @Test
    @DisplayName("동일한 좌표 간 거리는 0m")
    void distanceBetweenSameCoordinatesIsZero() {
      // given
      GpsCoordinate coord1 = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate coord2 = new GpsCoordinate(37.5665, 126.9780);

      // when
      double distance = coord1.distanceTo(coord2);

      // then
      assertThat(distance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("서울시청 → 광화문 거리 계산 (약 600m)")
    void distanceBetweenSeoulCityHallAndGwanghwamun() {
      // given
      GpsCoordinate seoulCityHall = new GpsCoordinate(37.5665, 126.9780); // 서울시청
      GpsCoordinate gwanghwamun = new GpsCoordinate(37.5720, 126.9769); // 광화문

      // when
      double distance = seoulCityHall.distanceTo(gwanghwamun);

      // then
      assertThat(distance).isBetween(600.0, 650.0); // 약 620m
    }

    @Test
    @DisplayName("서울 → 부산 거리 계산 (약 325km)")
    void distanceBetweenSeoulAndBusan() {
      // given
      GpsCoordinate seoul = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate busan = new GpsCoordinate(35.1796, 129.0756);

      // when
      double distance = seoul.distanceTo(busan);

      // then
      assertThat(distance).isBetween(320000.0, 330000.0); // 약 325km
    }

    @Test
    @DisplayName("거리 계산은 대칭적이어야 함 (A→B == B→A)")
    void distanceCalculationShouldBeSymmetric() {
      // given
      GpsCoordinate coord1 = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate coord2 = new GpsCoordinate(35.1796, 129.0756);

      // when
      double distance1 = coord1.distanceTo(coord2);
      double distance2 = coord2.distanceTo(coord1);

      // then
      assertThat(distance1).isEqualTo(distance2);
    }

    @Test
    @DisplayName("5m 이내 거리 정밀 측정")
    void distanceWithin5Meters() {
      // given
      GpsCoordinate coord1 = new GpsCoordinate(37.5665, 126.9780);
      // 약 4.5m 떨어진 지점 (위도 약 0.00004도 차이)
      GpsCoordinate coord2 = new GpsCoordinate(37.56654, 126.9780);

      // when
      double distance = coord1.distanceTo(coord2);

      // then
      assertThat(distance).isLessThan(5.0);
      assertThat(distance).isGreaterThan(4.0);
    }
  }

  @Nested
  @DisplayName("동등성 테스트")
  class EqualityTest {

    @Test
    @DisplayName("동일한 좌표는 equals true")
    void sameCoordinatesAreEqual() {
      // given
      GpsCoordinate coord1 = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate coord2 = new GpsCoordinate(37.5665, 126.9780);

      // when & then
      assertThat(coord1).isEqualTo(coord2);
      assertThat(coord1.hashCode()).isEqualTo(coord2.hashCode());
    }

    @Test
    @DisplayName("다른 좌표는 equals false")
    void differentCoordinatesAreNotEqual() {
      // given
      GpsCoordinate coord1 = new GpsCoordinate(37.5665, 126.9780);
      GpsCoordinate coord2 = new GpsCoordinate(37.5666, 126.9780);

      // when & then
      assertThat(coord1).isNotEqualTo(coord2);
    }

    @Test
    @DisplayName("자기 자신과 equals true")
    void coordinateEqualsItself() {
      // given
      GpsCoordinate coord = new GpsCoordinate(37.5665, 126.9780);

      // when & then
      assertThat(coord).isEqualTo(coord);
    }
  }
}
