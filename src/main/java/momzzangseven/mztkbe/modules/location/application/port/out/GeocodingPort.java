package momzzangseven.mztkbe.modules.location.application.port.out;

import momzzangseven.mztkbe.global.error.location.GeoCodingFailedException;
import momzzangseven.mztkbe.modules.location.application.dto.GeoCoordinates;

/** Geocoding service port - address -> GPS coordinate translation */
public interface GeocodingPort {

  /**
   * Convert address to GPS coordinates
   *
   * @param address
   * @return GeoCoordinates object
   * @throws GeoCodingFailedException
   */
  GeoCoordinates convertAddressToCoordinates(String address) throws GeoCodingFailedException;
}
