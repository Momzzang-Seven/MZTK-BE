package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.location.GeocodingFailedException;
import momzzangseven.mztkbe.global.error.location.ReverseGeocodingFailedException;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.client.KakaoGeocodingClient;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto.KakaoGeocodingResponse;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto.KakaoReverseGeocodingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KakaoGeocodingAdapter 단위 테스트")
class KakaoGeocodingAdapterTest {

  @Mock private KakaoGeocodingClient kakaoGeocodingClient;

  @InjectMocks private KakaoGeocodingAdapter kakaoGeocodingAdapter;

  @Nested
  @DisplayName("geocode() - 주소 → 좌표 변환")
  class GeocodeTest {

    @Test
    @DisplayName("도로명 주소로 좌표 변환 성공 (road_address 우선)")
    void geocodeWithRoadAddress() {
      // given
      String address = "서울특별시 중구 세종대로 110";

      KakaoGeocodingResponse.RoadAddress roadAddress = new KakaoGeocodingResponse.RoadAddress();
      roadAddress.setY("37.5665");
      roadAddress.setX("126.9780");

      KakaoGeocodingResponse.Document document = new KakaoGeocodingResponse.Document();
      document.setRoadAddress(roadAddress);

      KakaoGeocodingResponse response = new KakaoGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.geocode(address)).willReturn(response);

      // when
      CoordinatesInfo result = kakaoGeocodingAdapter.geocode(address);

      // then
      assertThat(result.latitude()).isEqualTo(37.5665);
      assertThat(result.longitude()).isEqualTo(126.9780);

      verify(kakaoGeocodingClient, times(1)).geocode(address);
    }

    @Test
    @DisplayName("지번 주소로 좌표 변환 성공 (road_address 없을 때)")
    void geocodeWithJibunAddress() {
      // given
      String address = "서울 중구 태평로1가 31";

      KakaoGeocodingResponse.Address jibunAddress = new KakaoGeocodingResponse.Address();
      jibunAddress.setY("37.5665");
      jibunAddress.setX("126.9780");

      KakaoGeocodingResponse.Document document = new KakaoGeocodingResponse.Document();
      document.setRoadAddress(null); // 도로명 주소 없음
      document.setAddress(jibunAddress);

      KakaoGeocodingResponse response = new KakaoGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.geocode(address)).willReturn(response);

      // when
      CoordinatesInfo result = kakaoGeocodingAdapter.geocode(address);

      // then
      assertThat(result.latitude()).isEqualTo(37.5665);
      assertThat(result.longitude()).isEqualTo(126.9780);
    }

    @Test
    @DisplayName("Document 레벨 좌표 사용 (address도 없을 때)")
    void geocodeWithDocumentLevelCoordinates() {
      // given
      String address = "서울시청";

      KakaoGeocodingResponse.Document document = new KakaoGeocodingResponse.Document();
      document.setRoadAddress(null);
      document.setAddress(null);
      document.setY("37.5665");
      document.setX("126.9780");

      KakaoGeocodingResponse response = new KakaoGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.geocode(address)).willReturn(response);

      // when
      CoordinatesInfo result = kakaoGeocodingAdapter.geocode(address);

      // then
      assertThat(result.latitude()).isEqualTo(37.5665);
      assertThat(result.longitude()).isEqualTo(126.9780);
    }

    @Test
    @DisplayName("null 주소로 geocode 호출 시 예외 발생")
    void geocodeWithNullAddressThrowsException() {
      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.geocode(null))
          .isInstanceOf(GeocodingFailedException.class)
          .hasMessageContaining("Address cannot be null or empty");

      verify(kakaoGeocodingClient, never()).geocode(anyString());
    }

    @Test
    @DisplayName("빈 문자열 주소로 geocode 호출 시 예외 발생")
    void geocodeWithBlankAddressThrowsException() {
      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.geocode("   "))
          .isInstanceOf(GeocodingFailedException.class)
          .hasMessageContaining("Address cannot be null or empty");

      verify(kakaoGeocodingClient, never()).geocode(anyString());
    }

    @Test
    @DisplayName("응답이 null이면 예외 발생")
    void geocodeWithNullResponseThrowsException() {
      // given
      String address = "서울시청";
      given(kakaoGeocodingClient.geocode(address)).willReturn(null);

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.geocode(address))
          .isInstanceOf(GeocodingFailedException.class)
          .hasMessageContaining("No geocoding results found for address");
    }

    @Test
    @DisplayName("응답의 documents가 비어있으면 예외 발생")
    void geocodeWithEmptyDocumentsThrowsException() {
      // given
      String address = "존재하지않는주소123456";
      KakaoGeocodingResponse response = new KakaoGeocodingResponse();
      response.setDocuments(Collections.emptyList());

      given(kakaoGeocodingClient.geocode(address)).willReturn(response);

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.geocode(address))
          .isInstanceOf(GeocodingFailedException.class)
          .hasMessageContaining("No geocoding results found for address");
    }

    @Test
    @DisplayName("API 호출 실패 시 GeocodingFailedException 발생")
    void geocodeApiCallFailureThrowsException() {
      // given
      String address = "서울시청";
      given(kakaoGeocodingClient.geocode(address))
          .willThrow(new RuntimeException("API call failed"));

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.geocode(address))
          .isInstanceOf(GeocodingFailedException.class)
          .hasMessageContaining("Unexpected error during geocoding");
    }
  }

  @Nested
  @DisplayName("reverseGeocode() - 좌표 → 주소 변환")
  class ReverseGeocodeTest {

    @Test
    @DisplayName("좌표로 도로명 주소 변환 성공 (road_address 우선)")
    void reverseGeocodeWithRoadAddress() {
      // given
      double longitude = 126.9780;
      double latitude = 37.5665;

      KakaoReverseGeocodingResponse.RoadAddress roadAddress =
          new KakaoReverseGeocodingResponse.RoadAddress();
      roadAddress.setAddressName("서울특별시 중구 세종대로 110");
      roadAddress.setZoneNo("04524");

      KakaoReverseGeocodingResponse.Document document =
          new KakaoReverseGeocodingResponse.Document();
      document.setRoadAddress(roadAddress);

      KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude)).willReturn(response);

      // when
      AddressInfo result = kakaoGeocodingAdapter.reverseGeocode(longitude, latitude);

      // then
      assertThat(result.address()).isEqualTo("서울특별시 중구 세종대로 110");
      assertThat(result.postalCode()).isEqualTo("04524");

      verify(kakaoGeocodingClient, times(1)).reverseGeocode(longitude, latitude);
    }

    @Test
    @DisplayName("좌표로 지번 주소 변환 성공 (road_address 없을 때)")
    void reverseGeocodeWithJibunAddress() {
      // given
      double longitude = 126.9780;
      double latitude = 37.5665;

      KakaoReverseGeocodingResponse.Address jibunAddress =
          new KakaoReverseGeocodingResponse.Address();
      jibunAddress.setAddressName("서울 중구 태평로1가 31");

      KakaoReverseGeocodingResponse.Document document =
          new KakaoReverseGeocodingResponse.Document();
      document.setRoadAddress(null); // 도로명 주소 없음
      document.setAddress(jibunAddress);

      KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude)).willReturn(response);

      // when
      AddressInfo result = kakaoGeocodingAdapter.reverseGeocode(longitude, latitude);

      // then
      assertThat(result.address()).isEqualTo("서울 중구 태평로1가 31");
      assertThat(result.postalCode()).isEqualTo(""); // 지번 주소는 우편번호 없음
    }

    @Test
    @DisplayName("우편번호가 null이면 빈 문자열로 설정")
    void reverseGeocodeWithNullPostalCode() {
      // given
      double longitude = 126.9780;
      double latitude = 37.5665;

      KakaoReverseGeocodingResponse.RoadAddress roadAddress =
          new KakaoReverseGeocodingResponse.RoadAddress();
      roadAddress.setAddressName("서울시 중구");
      roadAddress.setZoneNo(null); // 우편번호 없음

      KakaoReverseGeocodingResponse.Document document =
          new KakaoReverseGeocodingResponse.Document();
      document.setRoadAddress(roadAddress);

      KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude)).willReturn(response);

      // when
      AddressInfo result = kakaoGeocodingAdapter.reverseGeocode(longitude, latitude);

      // then
      assertThat(result.postalCode()).isEqualTo("");
    }

    @Test
    @DisplayName("응답이 null이면 예외 발생")
    void reverseGeocodeWithNullResponseThrowsException() {
      // given
      double longitude = 999.0;
      double latitude = 999.0;
      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude)).willReturn(null);

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.reverseGeocode(longitude, latitude))
          .isInstanceOf(ReverseGeocodingFailedException.class)
          .hasMessageContaining("No reverse geocoding results found for coordinates");
    }

    @Test
    @DisplayName("응답의 documents가 비어있으면 예외 발생")
    void reverseGeocodeWithEmptyDocumentsThrowsException() {
      // given
      double longitude = 0.0;
      double latitude = 0.0;
      KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();
      response.setDocuments(Collections.emptyList());

      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude)).willReturn(response);

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.reverseGeocode(longitude, latitude))
          .isInstanceOf(ReverseGeocodingFailedException.class)
          .hasMessageContaining("No reverse geocoding results found for coordinates");
    }

    @Test
    @DisplayName("road_address와 address 모두 없으면 예외 발생")
    void reverseGeocodeWithNoAddressThrowsException() {
      // given
      double longitude = 126.9780;
      double latitude = 37.5665;

      KakaoReverseGeocodingResponse.Document document =
          new KakaoReverseGeocodingResponse.Document();
      document.setRoadAddress(null);
      document.setAddress(null);

      KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();
      response.setDocuments(List.of(document));

      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude)).willReturn(response);

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.reverseGeocode(longitude, latitude))
          .isInstanceOf(ReverseGeocodingFailedException.class)
          .hasMessageContaining("No valid address found in reverse geocoding response");
    }

    @Test
    @DisplayName("API 호출 실패 시 ReverseGeocodingFailedException 발생")
    void reverseGeocodeApiCallFailureThrowsException() {
      // given
      double longitude = 126.9780;
      double latitude = 37.5665;
      given(kakaoGeocodingClient.reverseGeocode(longitude, latitude))
          .willThrow(new RuntimeException("API call failed"));

      // when & then
      assertThatThrownBy(() -> kakaoGeocodingAdapter.reverseGeocode(longitude, latitude))
          .isInstanceOf(ReverseGeocodingFailedException.class)
          .hasMessageContaining("Unexpected error during reverse geocoding");
    }
  }
}
