package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.*;

import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;
import momzzangseven.mztkbe.modules.location.api.dto.RegisterLocationRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterLocationCommand 단위 테스트")
class RegisterLocationCommandTest {

  @Nested
  @DisplayName("정적 팩토리 메서드 테스트")
  class FactoryMethodTest {

    @Test
    @DisplayName("from() 메서드로 RequestDTO에서 Command 생성")
    void createCommandFromRequestDTO() {
      // given
      Long userId = 123L;
      RegisterLocationRequestDTO request =
          new RegisterLocationRequestDTO("서울대 체육관", "08826", "서울시 관악구", "2층", 37.46, 126.95);

      // when
      RegisterLocationCommand command = RegisterLocationCommand.from(userId, request);

      // then
      assertThat(command.userId()).isEqualTo(userId);
      assertThat(command.locationName()).isEqualTo("서울대 체육관");
      assertThat(command.postalCode()).isEqualTo("08826");
      assertThat(command.address()).isEqualTo("서울시 관악구");
      assertThat(command.detailAddress()).isEqualTo("2층");
      assertThat(command.latitude()).isEqualTo(37.46);
      assertThat(command.longitude()).isEqualTo(126.95);
    }

    @Test
    @DisplayName("from() 메서드는 null 값도 그대로 전달")
    void createCommandWithNullValues() {
      // given
      RegisterLocationRequestDTO request =
          new RegisterLocationRequestDTO("테스트", null, null, null, null, null);

      // when
      RegisterLocationCommand command = RegisterLocationCommand.from(999L, request);

      // then
      assertThat(command.postalCode()).isNull();
      assertThat(command.address()).isNull();
      assertThat(command.detailAddress()).isNull();
      assertThat(command.latitude()).isNull();
      assertThat(command.longitude()).isNull();
    }
  }

  @Nested
  @DisplayName("유효성 검증 테스트")
  class ValidationTest {

    @Test
    @DisplayName("주소 정보가 있으면 validate() 성공")
    void validateSucceedsWithAddressInfo() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThatCode(() -> command.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("좌표 정보가 있으면 validate() 성공")
    void validateSucceedsWithCoordinatesInfo() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress(null)
              .latitude(37.5)
              .longitude(127.0)
              .build();

      // when & then
      assertThatCode(() -> command.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주소와 좌표 모두 있으면 validate() 성공")
    void validateSucceedsWithBoth() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(37.5)
              .longitude(127.0)
              .build();

      // when & then
      assertThatCode(() -> command.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주소와 좌표 둘 다 없으면 validate() 실패")
    void validateFailsWithoutBoth() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> command.validate())
          .isInstanceOf(MissingLocationInfoException.class)
          .hasMessageContaining("Either address or GPS coordinates must be provided");
    }

    @Test
    @DisplayName("postalCode만 있고 address가 없으면 validate() 실패")
    void validateFailsWithPostalCodeOnly() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address(null)
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> command.validate()).isInstanceOf(MissingLocationInfoException.class);
    }

    @Test
    @DisplayName("address만 있고 postalCode가 없으면 validate() 실패")
    void validateFailsWithAddressOnly() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> command.validate()).isInstanceOf(MissingLocationInfoException.class);
    }

    @Test
    @DisplayName("latitude만 있고 longitude가 없으면 validate() 실패")
    void validateFailsWithLatitudeOnly() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress(null)
              .latitude(37.5)
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> command.validate()).isInstanceOf(MissingLocationInfoException.class);
    }

    @Test
    @DisplayName("주소가 빈 문자열이면 validate() 실패")
    void validateFailsWithBlankAddress() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address("   ") // 공백
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> command.validate()).isInstanceOf(MissingLocationInfoException.class);
    }
  }

  @Nested
  @DisplayName("hasAddressInfo() 테스트")
  class HasAddressInfoTest {

    @Test
    @DisplayName("postalCode와 address 모두 있으면 true")
    void returnsTrueWhenBothPresent() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThat(command.hasAddressInfo()).isTrue();
    }

    @Test
    @DisplayName("postalCode가 null이면 false")
    void returnsFalseWhenPostalCodeNull() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThat(command.hasAddressInfo()).isFalse();
    }

    @Test
    @DisplayName("address가 빈 문자열이면 false")
    void returnsFalseWhenAddressBlank() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("12345")
              .address("   ")
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThat(command.hasAddressInfo()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasCoordinatesInfo() 테스트")
  class HasCoordinatesInfoTest {

    @Test
    @DisplayName("latitude와 longitude 모두 있으면 true")
    void returnsTrueWhenBothPresent() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress(null)
              .latitude(37.5)
              .longitude(127.0)
              .build();

      // when & then
      assertThat(command.hasCoordinatesInfo()).isTrue();
    }

    @Test
    @DisplayName("latitude가 null이면 false")
    void returnsFalseWhenLatitudeNull() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress(null)
              .latitude(null)
              .longitude(127.0)
              .build();

      // when & then
      assertThat(command.hasCoordinatesInfo()).isFalse();
    }

    @Test
    @DisplayName("longitude가 null이면 false")
    void returnsFalseWhenLongitudeNull() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress(null)
              .latitude(37.5)
              .longitude(null)
              .build();

      // when & then
      assertThat(command.hasCoordinatesInfo()).isFalse();
    }
  }
}
