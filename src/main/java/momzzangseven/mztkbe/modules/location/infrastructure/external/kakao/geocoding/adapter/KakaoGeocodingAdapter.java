package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.location.GeocodingFailedException;
import momzzangseven.mztkbe.global.error.location.ReverseGeocodingFailedException;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;
import momzzangseven.mztkbe.modules.location.application.port.out.GeocodingPort;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.client.KakaoGeocodingClient;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto.KakaoGeocodingResponse;
import momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto.KakaoReverseGeocodingResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Geocoding Adapter - Geocoding port implementation - Using Kakao API */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoGeocodingAdapter implements GeocodingPort {
  private final KakaoGeocodingClient kakaoGeocodingClient;

  @Override
  public CoordinatesInfo geocode(String address) throws GeocodingFailedException {
    log.info("Geocoding request initiated");

    try {
      if (address == null || address.isBlank()) {
        throw new GeocodingFailedException("Address cannot be null or empty");
      }

      // Call Kakao API
      KakaoGeocodingResponse response = kakaoGeocodingClient.geocode(address);

      // Validate Response - No results found
      if (response == null || !response.hasDocuments()) {
        log.warn("No geocoding results found");
        throw new GeocodingFailedException("No geocoding results found for addres");
      }

      // First element of documents: most relevant address
      KakaoGeocodingResponse.Document document = response.getDocuments().get(0);

      // Extract coordinates
      double latitude;
      double longitude;

      if (document.getRoadAddress() != null) {
        latitude = Double.parseDouble(document.getRoadAddress().getY());
        longitude = Double.parseDouble(document.getRoadAddress().getX());
        log.debug("Using road address coordinates");
      } else if (document.getAddress() != null) {
        latitude = Double.parseDouble(document.getAddress().getY());
        longitude = Double.parseDouble(document.getAddress().getX());
        log.debug("Using jibun address coordinates");
      } else {
        latitude = Double.parseDouble(document.getY());
        longitude = Double.parseDouble(document.getX());
        log.debug("Using document coordinates");
      }

      log.info("Geocoding completed successfully");

      return CoordinatesInfo.of(latitude, longitude);

    } catch (HttpClientErrorException e) {
      // 4xx error - input error
      if (e.getStatusCode().value() == 400) {
        log.error("Invalid address format for geocoding: status={}", e.getStatusCode());
        throw new GeocodingFailedException("Invalid address format");
      }
      log.error("Geocoding client error: status={}", e.getStatusCode(), e);
      throw new GeocodingFailedException("Address search request error");
    } catch (HttpServerErrorException e) {
      // 5xx error - external API error
      log.error("Kakao Geocoding API server error: status={}", e.getStatusCode(), e);
      throw new GeocodingFailedException(
          "Kakao Geocoding API server error. Please try again later.");
    } catch (ResourceAccessException e) {
      // Network/timeout error - external API error
      log.error("Kakao Geocoding API connection error (timeout or network issue)", e);
      throw new GeocodingFailedException(
          "Kakao Geocoding API connection failed (network error or timeout). Please try again later.");
    } catch (GeocodingFailedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during geocoding", e);
      throw new GeocodingFailedException("Unexpected error during geocoding");
    }
  }

  @Override
  public AddressInfo reverseGeocode(double longitude, double latitude)
      throws ReverseGeocodingFailedException {

    log.info("Reverse geocoding request initiated");

    try {
      // Call Kakao API (NOTE: Must hand over in order longitude(x) followed by latitude(y))
      KakaoReverseGeocodingResponse response =
          kakaoGeocodingClient.reverseGeocode(longitude, latitude);

      // Validate Response - No results found
      if (response == null || !response.hasDocuments()) {
        log.warn("No reverse geocoding results found");
        throw new ReverseGeocodingFailedException(
            "No reverse geocoding results found for coordinates");
      }

      // First element of documents: most relevant address
      KakaoReverseGeocodingResponse.Document document = response.getDocuments().get(0);

      // Extract Address
      String addressName;
      String zoneNo;

      if (document.getRoadAddress() != null) {
        addressName = document.getRoadAddress().getAddressName();
        zoneNo = document.getRoadAddress().getZoneNo();
        log.debug("Using road address");
      } else if (document.getAddress() != null) {
        addressName = document.getAddress().getAddressName();
        zoneNo = ""; // Only RoadAddress contains zoneNo
        log.debug("Using jibun address");
      } else {
        log.warn("No valid address found in reverse geocoding response");
        throw new ReverseGeocodingFailedException(
            "No valid address found in reverse geocoding response");
      }

      // Validate zoneNo (If null or empty, set it "")
      if (zoneNo == null || zoneNo.isBlank()) {
        log.warn("No postal code found in geocoding result");
        zoneNo = "";
      }

      log.info("Reverse geocoding completed successfully");

      return AddressInfo.of(addressName, zoneNo);

    } catch (HttpClientErrorException e) {
      // 4xx error - input error
      if (e.getStatusCode().value() == 400) {
        log.error("Invalid coordinates for reverse geocoding: status={}", e.getStatusCode());
        throw new ReverseGeocodingFailedException(
            String.format("Invalid coordinates for reverse geocoding"));
      }
      log.error("Reverse geocoding client error: status={}", e.getStatusCode(), e);
      throw new ReverseGeocodingFailedException("Reverse geocoding request error");
    } catch (HttpServerErrorException e) {
      // 5xx error - external API error
      log.error("Kakao Reverse Geocoding API server error: status={}", e.getStatusCode(), e);
      throw new ReverseGeocodingFailedException(
          "Kakao Reverse Geocoding API server error. Please try again later.");
    } catch (ResourceAccessException e) {
      // Network/timeout error - external API error
      log.error("Kakao Reverse Geocoding API connection error (timeout or network issue)", e);
      throw new ReverseGeocodingFailedException(
          "Kakao Reverse Geocoding API connection failed (network error or timeout). Please try again later.");
    } catch (ReverseGeocodingFailedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during reverse geocoding", e);
      throw new ReverseGeocodingFailedException(
          String.format("Unexpected error during reverse geocoding"));
    }
  }
}
