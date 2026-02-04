package momzzangseven.mztkbe.modules.location.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.location.application.dto.GeoCoordinates;
import momzzangseven.mztkbe.modules.location.application.dto.LocationItem;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.in.RegisterLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.GeocodingPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
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

    // Confirm GPS coordinates: Use it if provided, o.w, Geocoding
    GpsCoordinate coordinate = resolveGpsCoordinate(command);

    // Create Location domain model
    Location location =
        Location.create(
            command.userId(),
            command.locationName(),
            command.postalCode(),
            command.address(),
            command.detailAddress(),
            coordinate);

    // Save the location object
    Location savedLocation = saveLocationPort.save(location);
    log.info("Location registered successfully: id={}", savedLocation.getId());

    // Return the result
    LocationItem item = LocationItem.from(savedLocation);
    return RegisterLocationResult.from(item, command.userId());
  }

  /**
   * Confirm GPS coordinates - Use it as it is, if provided. - If not provided, call Geocoding API.
   */
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
    GeoCoordinates geoCoordinates = geocodingPort.convertAddressToCoordinates(command.address());

    return new GpsCoordinate(geoCoordinates.latitude(), geoCoordinates.longitude());
  }
}
