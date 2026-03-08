package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.config.GeocodingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KakaoGeocodingClientTest {

  @Mock private RestClient kakaoRestClient;
  @Mock private GeocodingConfig geocodingConfig;

  @InjectMocks private KakaoGeocodingClient client;

  @Test
  void geocode_shouldWrapUnexpectedException() {
    when(kakaoRestClient.get()).thenThrow(new RuntimeException("network down"));

    assertThatThrownBy(() -> client.geocode("Seoul"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to call Kakao Geocoding API");
  }

  @Test
  void reverseGeocode_shouldWrapUnexpectedException() {
    when(kakaoRestClient.get()).thenThrow(new RuntimeException("network down"));

    assertThatThrownBy(() -> client.reverseGeocode(126.9780, 37.5665))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to call Kakao Reverse Geocoding API");
  }
}
