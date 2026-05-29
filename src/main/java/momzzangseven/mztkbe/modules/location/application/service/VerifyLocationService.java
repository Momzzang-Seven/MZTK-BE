package momzzangseven.mztkbe.modules.location.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveVerificationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.VerificationRadius;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verify Location Service (T1)
 *
 * <p>Verifies whether the user is at the registered location using GPS and persists the result
 * (success/failure) as an audit record.
 *
 * <p>XP granting is orchestrated separately by {@code VerifyLocationFacade} so that the request
 * never holds two DB connections at once.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VerifyLocationService {

  private final LoadLocationPort loadLocationPort;
  private final SaveVerificationPort saveVerificationPort;
  private final VerificationRadius verificationRadius;

  /** Verifies the location and saves the verification record, returning the saved entity. */
  public LocationVerification verify(VerifyLocationCommand command) {
    log.info("Location verification initiated: locationId={}", command.locationId());

    Location location =
        loadLocationPort
            .findByLocationId(command.locationId())
            .orElseThrow(
                () ->
                    new LocationNotFoundException(
                        "Location not found: id=" + command.locationId()));

    if (!location.isOwnedBy(command.userId())) {
      log.warn(
          "Unauthorized location access attempt: userId={}, locationId={}",
          command.userId(),
          command.locationId());
      throw new UserNotAuthenticatedException("You can only verify your own locations");
    }

    LocationVerification verification =
        LocationVerification.create(
            command.userId(), location, command.currentCoordinate(), verificationRadius);

    LocationVerification saved = saveVerificationPort.save(verification);

    log.info(
        "Location verification completed: locationId={}, isVerified={}, distance={}m",
        command.locationId(),
        saved.isVerified(),
        saved.getDistance());

    return saved;
  }
}
