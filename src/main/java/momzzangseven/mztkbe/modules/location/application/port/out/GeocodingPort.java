package momzzangseven.mztkbe.modules.location.application.port.out;

import momzzangseven.mztkbe.global.error.location.GeoCodingFailedException;
import momzzangseven.mztkbe.global.error.location.ReverseGeoCodingFailedException;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;

/** Geocoding service port - address -> GPS coordinate translation */
public interface GeocodingPort {

  /**
   * Convert address to GPS coordinates (Geocoding)
   *
   * @param address
   * @return CoordinatesInfo object
   * @throws GeoCodingFailedException
   */
  CoordinatesInfo geocode(String address) throws GeoCodingFailedException;

  /**
   * Convert GPS coordinates to address (Reverse Geocoding)
   *
   * @param latitude
   * @param longitude
   * @return Address Info (address, postalCode)
   * @throws ReverseGeoCodingFailedException
   */
  AddressInfo reverseGeocode(double latitude, double longitude)
      throws ReverseGeoCodingFailedException;
}
