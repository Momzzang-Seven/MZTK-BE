package momzzangseven.mztkbe.modules.location.application.service;

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
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.VerificationRadius;
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
  private final XpGrantService xpGrantService;
  private final VerificationRadius verificationRadius; // ← DI added

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
      xpInfo = grantXpForVerification(saved);
    } else {
      xpInfo = new XpGrantInfo(false, 0, "Verification failed - XP not granted");
    }

    return VerifyLocationResult.from(saved, xpInfo);
  }

  /**
   * Grant XP when location verification is successful
   *
   * <p>Business rule: location verification = workout verification (WORKOUT XpType used)
   *
   * <p>Idempotency guarantee: prevent duplicate XP grant for the same verification
   */
  private XpGrantInfo grantXpForVerification(LocationVerification verification) {
    try {
      // Transaction A suspended
      // Transaction B starts (REQUIRES_NEW)
      int grantedXp = xpGrantService.grantXp(verification);
      // Transaction B committed (grantedXp = 0 also committed)
      // Transaction A resumed (resume)

      if (grantedXp > 0) {
        log.info(
            "XP granted for location verification: userId={}, xp={}",
            verification.getUserId(),
            grantedXp);
        return new XpGrantInfo(true, grantedXp, "XP granted successfully");
      } else {
        log.info(
            "XP not granted for location verification: userId={} - ALREADY_GRANTED or DAILY_CAP_REACHED",
            verification.getUserId());
        return new XpGrantInfo(false, 0, "XP already granted for WORKOUT type");
      }

    } catch (Exception e) {
      // System error occurred in Level module
      // XP grant failure does not roll back the entire transaction
      // Verification record must be saved
      log.error(
          "Failed to grant XP for location verification: userId={}", verification.getUserId(), e);
      return new XpGrantInfo(false, 0, "XP grant failed due to system error");
    }
  }
}
