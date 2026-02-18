package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.config.GeocodingConfig;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto.KakaoGeocodingResponse;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto.KakaoReverseGeocodingResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Kakao Geocoding API Client */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoGeocodingClient {
  private final RestClient kakaoRestClient;
  private final GeocodingConfig geocodingConfig;

  /**
   * Convert Address → GPS coordinates (Geocoding)
   *
   * @param address
   * @return Kakao API Response
   */
  public KakaoGeocodingResponse geocode(String address) {
    try {
      log.debug("Kakao Geocoding API Request initiated");

      KakaoGeocodingResponse response =
          kakaoRestClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(geocodingConfig.getAddressSearchPath())
                          .queryParam("query", address) // Spring이 자동 인코딩
                          .build())
              .retrieve()
              .body(KakaoGeocodingResponse.class);

      if (response != null && response.getMeta() != null) {
        log.debug(
            "Kakao Geocoding API Response received: totalCount={}",
            response.getMeta().getTotalCount());
      }

      return response;

    } catch (Exception e) {
      log.error("Kakao Geocoding API call failed", e);
      throw new BusinessException(
          ErrorCode.EXTERNAL_API_ERROR,
          "Failed to call Kakao Geocoding API for address: " + address);
    }
  }

  /**
   * Convert GPS coordinates → Address (Reverse Geocoding)
   *
   * @param longitude x
   * @param latitude y
   * @return Kakao API Response
   */
  public KakaoReverseGeocodingResponse reverseGeocode(double longitude, double latitude) {
    try {
      log.debug("Kakao Reverse Geocoding API Request initiated");

      KakaoReverseGeocodingResponse response =
          kakaoRestClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(geocodingConfig.getCoordToAddressPath())
                          .queryParam("x", longitude)
                          .queryParam("y", latitude)
                          .build())
              .retrieve()
              .body(KakaoReverseGeocodingResponse.class);

      if (response != null && response.getMeta() != null) {
        log.debug(
            "Kakao Reverse Geocoding API Response received: totalCount={}",
            response.getMeta().getTotalCount());
      }

      return response;

    } catch (Exception e) {
      log.error("Kakao Reverse Geocoding API call failed", e);
      throw new BusinessException(
          ErrorCode.EXTERNAL_API_ERROR,
          "Failed to call Kakao Reverse Geocoding API for coordinates: "
              + longitude
              + ", "
              + latitude);
    }
  }
}
