package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Geocoding API configuration */
@Getter
@Configuration
public class GeocodingConfig {
  @Value("${kakao.api.rest-api-key}")
  private String restApiKey;

  @Value("${kakao.api.geocoding.base-url}")
  private String baseUrl;

  @Value("${kakao.api.geocoding.address-search-path}")
  private String addressSearchPath;

  @Value("${kakao.api.geocoding.coord-to-address-path}")
  private String coordToAddressPath;

  @Value("${kakao.api.geocoding.timeout:5000}")
  private int timeout;

  /** RestClient Bean (Spring 6.1+) */
  @Bean
  public RestClient kakaoRestClient() {
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "KakaoAK " + restApiKey)
        .build();
  }
}
