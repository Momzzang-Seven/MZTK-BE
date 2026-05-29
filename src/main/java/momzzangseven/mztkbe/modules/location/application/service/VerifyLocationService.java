package momzzangseven.mztkbe.modules.location.application.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.dto.XpGrantInfo;
import momzzangseven.mztkbe.modules.location.application.port.in.VerifyLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveVerificationPort;
import momzzangseven.mztkbe.modules.location.domain.event.LocationVerifiedEvent;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.VerificationRadius;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verify Location Service
 *
 * <p>Location verification service. Verify if the user is actually at the registered location using
 * GPS.
 *
 * <p>Grant XP when verification is successful and all verification attempts are logged as audit
 * logs.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VerifyLocationService implements VerifyLocationUseCase {

  private final LoadLocationPort loadLocationPort;
  private final SaveVerificationPort saveVerificationPort;
  private final ApplicationEventPublisher eventPublisher;
  private final VerificationRadius verificationRadius; // ← DI added
  private final ZoneId appZoneId;

  @Override
  public VerifyLocationResult execute(VerifyLocationCommand command) {
    log.info("Location verification initiated: locationId={}", command.locationId());

    // 1. Load location
    Location location =
        loadLocationPort
            .findByLocationId(command.locationId())
            .orElseThrow(
                () ->
                    new LocationNotFoundException(
                        "Location not found: id=" + command.locationId()));

    // 2. Verify ownership (only own locations can be verified)
    if (!location.isOwnedBy(command.userId())) {
      log.warn(
          "Unauthorized location access attempt: userId={}, locationId={}",
          command.userId(),
          command.locationId());
      throw new UserNotAuthenticatedException("You can only verify your own locations");
    }

    // 3. Create verification result (calculate distance + verification decision)
    LocationVerification verification =
        LocationVerification.create(
            command.userId(),
            location,
            command.currentCoordinate(),
            verificationRadius // ← injected configuration passed
            );

    // 4. Save verification record (success/failure, audit log)
    LocationVerification saved = saveVerificationPort.save(verification);

    log.info(
        "Location verification completed: locationId={}, isVerified={}, distance={}m",
        command.locationId(),
        saved.isVerified(),
        saved.getDistance());

    // 5. Grant XP (only when verification is successful)
    // If the user already acquired XP for WORKOUT type, the XpGrantInfo contains the information
    // that the user cannot be granted more XP for WORKOUT type.
    XpGrantInfo xpInfo;

    if (saved.isSuccessful()) {
      // Publish inside this transaction; the level module grants WORKOUT XP on AFTER_COMMIT so the
      // verification never holds a second connection while granting XP (see XpGrantEventHandler).
      eventPublisher.publishEvent(
          new LocationVerifiedEvent(
              saved.getUserId(),
              saved.getLocationId(),
              LocalDateTime.ofInstant(saved.getVerifiedAt(), appZoneId)));
      // The actual grant is asynchronous, so the response reports the verification outcome with the
      // standard reward amount rather than the (now unknown) granted value (MOM-465 decision).
      xpInfo = new XpGrantInfo(true, 100, "XP granted successfully");
    } else {
      xpInfo = new XpGrantInfo(false, 0, "Verification failed - XP not granted");
    }

    return VerifyLocationResult.from(saved, xpInfo);
  }
}
