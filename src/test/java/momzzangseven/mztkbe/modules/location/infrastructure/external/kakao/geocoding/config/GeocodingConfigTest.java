package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class GeocodingConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(GeocodingConfig.class)
          .withPropertyValues(
              "kakao.api.rest-api-key=test-key",
              "kakao.api.geocoding.base-url=https://dapi.kakao.com",
              "kakao.api.geocoding.address-search-path=/v2/local/search/address.json",
              "kakao.api.geocoding.coord-to-address-path=/v2/local/geo/coord2address.json",
              "kakao.api.geocoding.timeout=5000");

  @Test
  void kakaoRestClient_shouldBuildClientFromConfiguredProperties() {
    contextRunner.run(
        context -> {
          GeocodingConfig config = context.getBean(GeocodingConfig.class);
          RestClient client = context.getBean(RestClient.class);

          assertThat(client).isNotNull();
          assertThat(config.getBaseUrl()).isEqualTo("https://dapi.kakao.com");
          assertThat(config.getRestApiKey()).isEqualTo("test-key");
          assertThat(config.getAddressSearchPath()).isEqualTo("/v2/local/search/address.json");
          assertThat(config.getCoordToAddressPath()).isEqualTo("/v2/local/geo/coord2address.json");
          assertThat(config.getTimeout()).isEqualTo(5000);
        });
  }
}
