package momzzangseven.mztkbe.modules.location.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.location.application.dto.*;
import momzzangseven.mztkbe.modules.location.application.port.in.RegisterLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.GeocodingPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.AddressData;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for location registration */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RegisterLocationService implements RegisterLocationUseCase {

  private final SaveLocationPort saveLocationPort;
  private final GeocodingPort geocodingPort;

  @Override
  public RegisterLocationResult execute(RegisterLocationCommand command) {
    log.info("Registering location for user: {}", command.userId());

    // validate the command
    command.validate();

    // Confirm GPS information (provided or geocoding)
    GpsCoordinate coordinate = resolveGpsCoordinate(command);

    // Confirm address information (provided or reverse-geocoding)
    AddressData address = resolveAddress(command, coordinate);

    // Create Location domain model
    Location location =
        Location.create(command.userId(), command.locationName(), coordinate, address);

    // Save the location object
    Location savedLocation = saveLocationPort.save(location);
    log.info("Location registered successfully: id={}", savedLocation.getId());

    // Return the result
    LocationItem item = LocationItem.from(savedLocation);
    return RegisterLocationResult.from(item, command.userId());
  }

  /** Confirm GPS coordinates - Use it as it is, if provided. o.w, call Geocoding API. */
  private GpsCoordinate resolveGpsCoordinate(RegisterLocationCommand command) {
    if (command.latitude() != null && command.longitude() != null) {
      log.debug(
          "Using provided GPS coordinates: lat={}, lng={}",
          command.latitude(),
          command.longitude());
      return new GpsCoordinate(command.latitude(), command.longitude());
    }

    log.debug(
        "GPS coordinates not provided, calling Geocoding API for address: {}", command.address());
    CoordinatesInfo coordinatesInfo = geocodingPort.geocode(command.address());

    return new GpsCoordinate(coordinatesInfo.latitude(), coordinatesInfo.longitude());
  }

  /**
   * Confirm address information - Use it as it is, if provided, o.w, call Reverse-Geocoding API.
   *
   * @param command
   * @param coordinate
   * @return
   */
  private AddressData resolveAddress(RegisterLocationCommand command, GpsCoordinate coordinate) {
    if (command.hasAddressInfo()) {
      // If address info is provided, use it as it is

      log.debug("Using provided address: {}", command.address());
      return new AddressData(command.address(), command.postalCode(), command.detailAddress());
    }

    // If only GPS is provided, reverse-geocoding
    log.debug(
        "Address not provided, reverse geocoding coordinates: lat={}, lng={}",
        coordinate.getLatitude(),
        coordinate.getLongitude());
    AddressInfo addressInfo =
        geocodingPort.reverseGeocode(coordinate.getLongitude(), coordinate.getLatitude());

    return new AddressData(
        addressInfo.address(), addressInfo.postalCode(), command.detailAddress());
  }
}
