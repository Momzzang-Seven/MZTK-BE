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

/** Geocoding Adapter - Geocoding port implementation - Using Kakao API */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoGeocodingAdapter implements GeocodingPort {
  private final KakaoGeocodingClient kakaoGeocodingClient;

  @Override
  public CoordinatesInfo geocode(String address) throws GeocodingFailedException {
    log.info("Geocoding address: {}", address);

    try {
      if (address == null || address.isBlank()) {
        throw new GeocodingFailedException("Address cannot be null or empty");
      }

      // Call Kakao API
      KakaoGeocodingResponse response = kakaoGeocodingClient.geocode(address);

      // Validate Response
      if (response == null || !response.hasDocuments()) {
        throw new GeocodingFailedException("No results found for address: " + address);
      }

      // First element of documents: most relevant address
      KakaoGeocodingResponse.Document document = response.getDocuments().get(0);

      // Extract coordinates
      double latitude;
      double longitude;

      if (document.getRoadAddress() != null) {
        latitude = Double.parseDouble(document.getRoadAddress().getY());
        longitude = Double.parseDouble(document.getRoadAddress().getX());
        log.debug("Using road address coordinates: lat={}, lng={}", latitude, longitude);
      } else if (document.getAddress() != null) {
        latitude = Double.parseDouble(document.getAddress().getY());
        longitude = Double.parseDouble(document.getAddress().getX());
        log.debug("Using address coordinates: lat={}, lng={}", latitude, longitude);
      } else {
        latitude = Double.parseDouble(document.getY());
        longitude = Double.parseDouble(document.getX());
        log.debug("Using document coordinates: lat={}, lng={}", latitude, longitude);
      }

      log.info("Geocoding success: address={} → lat={}, lng={}", address, latitude, longitude);

      return CoordinatesInfo.of(latitude, longitude);

    } catch (GeocodingFailedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Geocoding failed: address={}", address, e);
      throw new GeocodingFailedException("Failed to geocode address: " + address);
    }
  }

  @Override
  public AddressInfo reverseGeocode(double longitude, double latitude)
      throws ReverseGeocodingFailedException {

    log.info("Reverse geocoding coordinates: lat={}, lng={}", longitude, latitude);

    try {
      // Call Kakao API (NOTE: Must hand over in order longitude(x) followed by latitude(y))
      KakaoReverseGeocodingResponse response =
          kakaoGeocodingClient.reverseGeocode(longitude, latitude);

      // Validate Response
      if (response == null || !response.hasDocuments()) {
        throw new ReverseGeocodingFailedException(
            String.format("No results found for coordinates: lat=%f, lng=%f", latitude, longitude));
      }

      // First element of documents: most relevant address
      KakaoReverseGeocodingResponse.Document document = response.getDocuments().get(0);

      // Extract Address
      String addressName;
      String zoneNo;

      if (document.getRoadAddress() != null) {
        addressName = document.getRoadAddress().getAddressName();
        zoneNo = document.getRoadAddress().getZoneNo();
        log.debug("Using road address: {}", addressName);
      } else if (document.getAddress() != null) {
        addressName = document.getAddress().getAddressName();
        zoneNo = ""; // Only RoadAddress contains zoneNo
        log.debug("Using jibun address: {}", addressName);
      } else {
        throw new ReverseGeocodingFailedException("No valid address found in response");
      }

      // Validate zoneNo (If null or empty, set it "")
      if (zoneNo == null || zoneNo.isBlank()) {
        log.warn("No postal code found for address: {}", addressName);
        zoneNo = "";
      }

      log.info(
          "Reverse geocoding success: lat={}, lng={} → address={}, zoneNo={}",
          latitude,
          longitude,
          addressName,
          zoneNo);

      return AddressInfo.of(addressName, zoneNo);

    } catch (ReverseGeocodingFailedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Reverse geocoding failed: lat={}, lng={}", latitude, longitude, e);
      throw new ReverseGeocodingFailedException(
          String.format(
              "Failed to reverse geocode coordinates: lat=%f, lng=%f", latitude, longitude));
    }
  }
}
