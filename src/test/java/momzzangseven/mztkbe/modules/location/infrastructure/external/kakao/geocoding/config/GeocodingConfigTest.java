package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class GeocodingConfigTest {

  @Test
  void kakaoRestClient_shouldBuildClientWithConfiguredValues() {
    GeocodingConfig config = new GeocodingConfig();
    ReflectionTestUtils.setField(config, "restApiKey", "test-key");
    ReflectionTestUtils.setField(config, "baseUrl", "https://dapi.kakao.com");
    ReflectionTestUtils.setField(config, "addressSearchPath", "/v2/local/search/address.json");
    ReflectionTestUtils.setField(config, "coordToAddressPath", "/v2/local/geo/coord2address.json");
    ReflectionTestUtils.setField(config, "timeout", 5000);

    RestClient client = config.kakaoRestClient();

    assertThat(client).isNotNull();
    assertThat(config.getBaseUrl()).isEqualTo("https://dapi.kakao.com");
    assertThat(config.getRestApiKey()).isEqualTo("test-key");
  }
}
