package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
      String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
      String path = geocodingConfig.getAddressSearchPath() + "?query=" + encodedAddress;

      log.debug("Kakao Geocoding API Request: address={}", address);

      KakaoGeocodingResponse response =
          kakaoRestClient.get().uri(path).retrieve().body(KakaoGeocodingResponse.class);

      log.debug(
          "Kakao Geocoding API Response: totalCount={}",
          response != null && response.getMeta() != null ? response.getMeta().getTotalCount() : 0);

      return response;

    } catch (Exception e) {
      log.error("Kakao Geocoding API call failed: address={}", address, e);
      throw new BusinessException(
          ErrorCode.EXTERNAL_API_ERROR, "Failed to call Kakao Geocoding API");
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
      String path = geocodingConfig.getCoordToAddressPath() + "?x=" + longitude + "&y=" + latitude;

      log.debug("Kakao Reverse Geocoding API Request: lng={}, lat={}", longitude, latitude);

      KakaoReverseGeocodingResponse response =
          kakaoRestClient.get().uri(path).retrieve().body(KakaoReverseGeocodingResponse.class);

      log.debug(
          "Kakao Reverse Geocoding API Response: totalCount={}",
          response != null && response.getMeta() != null ? response.getMeta().getTotalCount() : 0);

      return response;

    } catch (Exception e) {
      log.error("Kakao Reverse Geocoding API call failed: lng={}, lat={}", longitude, latitude, e);
      throw new BusinessException(
          ErrorCode.EXTERNAL_API_ERROR, "Failed to call Kakao Reverse Geocoding API");
    }
  }
}
