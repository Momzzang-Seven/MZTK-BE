package momzzangseven.mztkbe.modules.location.application.port.out;

import momzzangseven.mztkbe.global.error.location.GeocodingFailedException;
import momzzangseven.mztkbe.global.error.location.ReverseGeocodingFailedException;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;

/** Geocoding service port - address -> GPS coordinate translation */
public interface GeocodingPort {

  /**
   * Convert address to GPS coordinates (Geocoding)
   *
   * @param address
   * @return CoordinatesInfo object
   * @throws GeocodingFailedException
   */
  CoordinatesInfo geocode(String address) throws GeocodingFailedException;

  /**
   * Convert GPS coordinates to address (Reverse Geocoding)
   *
   * @param longitude
   * @param latitude
   * @return Address Info (address, postalCode)
   * @throws ReverseGeocodingFailedException
   */
  AddressInfo reverseGeocode(double longitude, double latitude)
      throws ReverseGeocodingFailedException;
}
