package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.config;

import java.time.Duration;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

  @Value("${kakao.api.geocoding.timeout}")
  private int timeout;

  /**
   * RestClient Bean with timeout configuration
   *
   * @return Kakao API용 RestClient
   */
  @Bean
  public RestClient kakaoRestClient() {
    // ClientHttpRequestFactory 설정
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(timeout)); // TCP connection timeout
    requestFactory.setReadTimeout(Duration.ofMillis(timeout)); // Read timeout

    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "KakaoAK " + restApiKey)
        .requestFactory(requestFactory)
        .build();
  }
}
