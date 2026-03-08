package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.out.GeocodingPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterLocationService 단위 테스트")
class RegisterLocationServiceTest {

  @Mock private SaveLocationPort saveLocationPort;

  @Mock private GeocodingPort geocodingPort;

  @InjectMocks private RegisterLocationService registerLocationService;

  @Nested
  @DisplayName("CASE 1: 주소 + 좌표 모두 제공")
  class Case1BothProvidedTest {

    @Test
    @DisplayName("주소와 좌표가 모두 제공되면 그대로 사용")
    void executeWithBothAddressAndCoordinates() {
      // given
      Long userId = 123L;
      String locationName = "서울대 체육관";
      String postalCode = "08826";
      String address = "서울특별시 관악구 관악로 1";
      String detailAddress = "2층 헬스장";
      Double latitude = 37.4601908;
      Double longitude = 126.9519817;

      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(userId)
              .locationName(locationName)
              .postalCode(postalCode)
              .address(address)
              .detailAddress(detailAddress)
              .latitude(latitude)
              .longitude(longitude)
              .build();

      Location savedLocation =
          Location.builder()
              .id(1L)
              .userId(userId)
              .locationName(locationName)
              .postalCode(postalCode)
              .address(address)
              .detailAddress(detailAddress)
              .coordinate(new GpsCoordinate(latitude, longitude))
              .registeredAt(Instant.now())
              .build();

      given(saveLocationPort.save(any(Location.class))).willReturn(savedLocation);

      // when
      RegisterLocationResult result = registerLocationService.execute(command);

      // then
      assertThat(result).isNotNull();
      assertThat(result.locationId()).isEqualTo(1L);
      assertThat(result.userId()).isEqualTo(userId);
      assertThat(result.locationName()).isEqualTo(locationName);
      assertThat(result.address()).isEqualTo(address);
      assertThat(result.postalCode()).isEqualTo(postalCode);
      assertThat(result.latitude()).isEqualTo(latitude);
      assertThat(result.longitude()).isEqualTo(longitude);

      // Geocoding API 호출되지 않음
      verify(geocodingPort, never()).geocode(anyString());
      verify(geocodingPort, never()).reverseGeocode(anyDouble(), anyDouble());

      // 저장은 1번 호출
      verify(saveLocationPort, times(1)).save(any(Location.class));
    }
  }

  @Nested
  @DisplayName("CASE 2: 주소만 제공 (지오코딩)")
  class Case2AddressOnlyTest {

    @Test
    @DisplayName("주소만 제공되면 지오코딩으로 좌표 계산")
    void executeWithAddressOnlyCallsGeocoding() {
      // given
      Long userId = 123L;
      String locationName = "서울시청";
      String postalCode = "04524";
      String address = "서울특별시 중구 세종대로 110";
      String detailAddress = null;

      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(userId)
              .locationName(locationName)
              .postalCode(postalCode)
              .address(address)
              .detailAddress(detailAddress)
              .latitude(null) // 좌표 미제공
              .longitude(null)
              .build();

      // Geocoding API 응답
      CoordinatesInfo geocodingResult = new CoordinatesInfo(37.5665, 126.9780);
      given(geocodingPort.geocode(address)).willReturn(geocodingResult);

      Location savedLocation =
          Location.builder()
              .id(1L)
              .userId(userId)
              .locationName(locationName)
              .postalCode(postalCode)
              .address(address)
              .detailAddress(detailAddress)
              .coordinate(new GpsCoordinate(37.5665, 126.9780))
              .registeredAt(Instant.now())
              .build();

      given(saveLocationPort.save(any(Location.class))).willReturn(savedLocation);

      // when
      RegisterLocationResult result = registerLocationService.execute(command);

      // then
      assertThat(result).isNotNull();
      assertThat(result.latitude()).isEqualTo(37.5665);
      assertThat(result.longitude()).isEqualTo(126.9780);

      // Geocoding 호출 확인
      verify(geocodingPort, times(1)).geocode(address);
      verify(geocodingPort, never()).reverseGeocode(anyDouble(), anyDouble());
      verify(saveLocationPort, times(1)).save(any(Location.class));
    }
  }

  @Nested
  @DisplayName("CASE 3: 좌표만 제공 (역지오코딩)")
  class Case3CoordinatesOnlyTest {

    @Test
    @DisplayName("좌표만 제공되면 역지오코딩으로 주소 계산")
    void executeWithCoordinatesOnlyCallsReverseGeocoding() {
      // given
      Long userId = 123L;
      String locationName = "내 헬스장";
      Double latitude = 37.5665;
      Double longitude = 126.9780;

      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(userId)
              .locationName(locationName)
              .postalCode(null) // 주소 미제공
              .address(null)
              .detailAddress(null)
              .latitude(latitude)
              .longitude(longitude)
              .build();

      // Reverse Geocoding API 응답
      AddressInfo reverseGeocodingResult = new AddressInfo("서울특별시 중구 세종대로 110", "04524");
      given(geocodingPort.reverseGeocode(longitude, latitude)).willReturn(reverseGeocodingResult);

      Location savedLocation =
          Location.builder()
              .id(1L)
              .userId(userId)
              .locationName(locationName)
              .postalCode("04524")
              .address("서울특별시 중구 세종대로 110")
              .detailAddress(null)
              .coordinate(new GpsCoordinate(latitude, longitude))
              .registeredAt(Instant.now())
              .build();

      given(saveLocationPort.save(any(Location.class))).willReturn(savedLocation);

      // when
      RegisterLocationResult result = registerLocationService.execute(command);

      // then
      assertThat(result).isNotNull();
      assertThat(result.address()).isEqualTo("서울특별시 중구 세종대로 110");
      assertThat(result.postalCode()).isEqualTo("04524");

      // Reverse Geocoding 호출 확인
      verify(geocodingPort, never()).geocode(anyString());
      verify(geocodingPort, times(1)).reverseGeocode(longitude, latitude);
      verify(saveLocationPort, times(1)).save(any(Location.class));
    }
  }

  @Nested
  @DisplayName("CASE 4: 주소와 좌표 둘 다 미제공 (에러)")
  class Case4NeitherProvidedTest {

    @Test
    @DisplayName("주소와 좌표 둘 다 없으면 예외 발생")
    void executeWithoutBothThrowsException() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null) // 주소 없음
              .address(null)
              .detailAddress(null)
              .latitude(null) // 좌표 없음
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> registerLocationService.execute(command))
          .isInstanceOf(MissingLocationInfoException.class)
          .hasMessageContaining("Either address or GPS coordinates must be provided");

      // API 호출 안 됨
      verify(geocodingPort, never()).geocode(anyString());
      verify(geocodingPort, never()).reverseGeocode(anyDouble(), anyDouble());
      verify(saveLocationPort, never()).save(any(Location.class));
    }

    @Test
    @DisplayName("주소가 빈 문자열이면 예외 발생")
    void executeWithBlankAddressThrowsException() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode("") // 빈 문자열
              .address("   ") // 공백
              .detailAddress(null)
              .latitude(null)
              .longitude(null)
              .build();

      // when & then
      assertThatThrownBy(() -> registerLocationService.execute(command))
          .isInstanceOf(MissingLocationInfoException.class);
    }
  }

  @Nested
  @DisplayName("DetailAddress 처리 테스트")
  class DetailAddressTest {

    @Test
    @DisplayName("detailAddress는 역지오코딩으로 얻을 수 없어 null 유지")
    void detailAddressRemainsNullAfterReverseGeocoding() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(123L)
              .locationName("테스트")
              .postalCode(null)
              .address(null)
              .detailAddress("2층 헬스장") // 사용자가 제공한 detailAddress
              .latitude(37.5665)
              .longitude(126.9780)
              .build();

      AddressInfo reverseGeocodingResult = new AddressInfo("서울시 중구 세종대로 110", "04524");
      given(geocodingPort.reverseGeocode(126.9780, 37.5665)).willReturn(reverseGeocodingResult);

      Location savedLocation =
          Location.builder()
              .id(1L)
              .userId(123L)
              .locationName("테스트")
              .postalCode("04524")
              .address("서울시 중구 세종대로 110")
              .detailAddress("2층 헬스장") // 사용자 제공값 유지
              .coordinate(new GpsCoordinate(37.5665, 126.9780))
              .registeredAt(Instant.now())
              .build();

      given(saveLocationPort.save(any(Location.class))).willReturn(savedLocation);

      // when
      RegisterLocationResult result = registerLocationService.execute(command);

      // then
      assertThat(result.detailAddress()).isEqualTo("2층 헬스장");
    }
  }

  @Nested
  @DisplayName("저장 검증 테스트")
  class SaveVerificationTest {

    @Test
    @DisplayName("저장된 Location이 ID를 포함해서 반환됨")
    void savedLocationIncludesId() {
      // given
      RegisterLocationCommand command =
          RegisterLocationCommand.builder()
              .userId(999L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .latitude(37.5)
              .longitude(127.0)
              .build();

      Location savedLocation =
          Location.builder()
              .id(100L) // DB에서 생성된 ID
              .userId(999L)
              .locationName("테스트")
              .postalCode("12345")
              .address("테스트 주소")
              .detailAddress(null)
              .coordinate(new GpsCoordinate(37.5, 127.0))
              .registeredAt(Instant.now())
              .build();

      given(saveLocationPort.save(any(Location.class))).willReturn(savedLocation);

      // when
      RegisterLocationResult result = registerLocationService.execute(command);

      // then
      assertThat(result.locationId()).isEqualTo(100L);
    }
  }
}
