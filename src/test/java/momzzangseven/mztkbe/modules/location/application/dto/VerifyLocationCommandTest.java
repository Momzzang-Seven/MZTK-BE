package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.*;

import momzzangseven.mztkbe.global.error.location.InvalidGpsCoordinateException;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerifyLocationCommand 단위 테스트")
class VerifyLocationCommandTest {

  @Nested
  @DisplayName("Factory Method: of()")
  class FactoryMethodTest {

    @Test
    @DisplayName("정상 생성")
    void createCommandSuccess() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      double currentLatitude = 37.4602015;
      double currentLongitude = 126.9520124;

      // when
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, currentLatitude, currentLongitude);

      // then
      assertThat(command.userId()).isEqualTo(userId);
      assertThat(command.locationId()).isEqualTo(locationId);
      assertThat(command.currentCoordinate()).isNotNull();
      assertThat(command.currentCoordinate().latitude()).isEqualTo(currentLatitude);
      assertThat(command.currentCoordinate().longitude()).isEqualTo(currentLongitude);
    }

    @Test
    @DisplayName("잘못된 GPS 좌표 - 위도 범위 초과")
    void createCommandWithInvalidLatitude() {
      // given
      Long userId = 123L;
      Long locationId = 1L;

      // when & then - 위도 > 90
      assertThatThrownBy(() -> VerifyLocationCommand.of(userId, locationId, 91.0, 126.9520124))
          .isInstanceOf(InvalidGpsCoordinateException.class)
          .hasMessageContaining("Latitude must be between -90 and 90");

      // 위도 < -90
      assertThatThrownBy(() -> VerifyLocationCommand.of(userId, locationId, -91.0, 126.9520124))
          .isInstanceOf(InvalidGpsCoordinateException.class)
          .hasMessageContaining("Latitude must be between -90 and 90");
    }

    @Test
    @DisplayName("잘못된 GPS 좌표 - 경도 범위 초과")
    void createCommandWithInvalidLongitude() {
      // given
      Long userId = 123L;
      Long locationId = 1L;

      // when & then - 경도 > 180
      assertThatThrownBy(() -> VerifyLocationCommand.of(userId, locationId, 37.4602015, 181.0))
          .isInstanceOf(InvalidGpsCoordinateException.class)
          .hasMessageContaining("Longitude must be between -180 and 180");

      // 경도 < -180
      assertThatThrownBy(() -> VerifyLocationCommand.of(userId, locationId, 37.4602015, -181.0))
          .isInstanceOf(InvalidGpsCoordinateException.class)
          .hasMessageContaining("Longitude must be between -180 and 180");
    }
  }

  @Nested
  @DisplayName("Constructor Validation")
  class ConstructorValidationTest {

    @Test
    @DisplayName("userId null 시 예외 발생")
    void userIdNull_throwsException() {
      // given
      GpsCoordinate coordinate = new GpsCoordinate(37.4602015, 126.9520124);

      // when & then
      assertThatThrownBy(() -> new VerifyLocationCommand(null, 1L, coordinate))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("userId is required");
    }

    @Test
    @DisplayName("locationId null 시 예외 발생")
    void locationIdNull_throwsException() {
      // given
      GpsCoordinate coordinate = new GpsCoordinate(37.4602015, 126.9520124);

      // when & then
      assertThatThrownBy(() -> new VerifyLocationCommand(123L, null, coordinate))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("locationId is required");
    }

    @Test
    @DisplayName("currentCoordinate null 시 예외 발생")
    void currentCoordinateNull_throwsException() {
      // when & then
      assertThatThrownBy(() -> new VerifyLocationCommand(123L, 1L, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("currentCoordinate is required");
    }

    @Test
    @DisplayName("모든 필드가 유효하면 생성 성공")
    void allFieldsValid_success() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      GpsCoordinate coordinate = new GpsCoordinate(37.4602015, 126.9520124);

      // when
      VerifyLocationCommand command = new VerifyLocationCommand(userId, locationId, coordinate);

      // then
      assertThat(command.userId()).isEqualTo(userId);
      assertThat(command.locationId()).isEqualTo(locationId);
      assertThat(command.currentCoordinate()).isEqualTo(coordinate);
    }
  }
}
