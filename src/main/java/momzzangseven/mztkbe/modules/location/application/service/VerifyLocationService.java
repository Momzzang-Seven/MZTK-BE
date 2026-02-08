package momzzangseven.mztkbe.modules.location.application.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.in.VerifyLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.GrantXpPort;
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
 * <p>Location verification service. Verify if the user is actually at the registered location using GPS.
 *
 * <p>Grant XP when verification is successful and all verification attempts are logged as audit logs.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VerifyLocationService implements VerifyLocationUseCase {
  private static final DateTimeFormatter YYYYMMDD_HHMMSS =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private final LoadLocationPort loadLocationPort;
  private final SaveVerificationPort saveVerificationPort;
  private final GrantXpPort grantXpPort;
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
    if (saved.shouldGrantXp()) {
      grantXpForVerification(saved);
    }

    return VerifyLocationResult.from(saved);
  }

  /**
   * Grant XP when location verification is successful
   *
   * <p>Business rule: location verification = workout verification (WORKOUT XpType used)
   *
   * <p>Idempotency guarantee: prevent duplicate XP grant for the same verification
   */
  private void grantXpForVerification(LocationVerification verification) {
    try {
      // Create Idempotency Key
      // Format: "workout:location-verify:{userId}:{locationId}:{timestamp}"
      // - Prefix "workout:" : GrantXpCommand verification passed
      // - "location-verify" : location verification within WORKOUT type
      String timestamp = verification.getVerifiedAt().atZone(appZoneId).format(YYYYMMDD_HHMMSS);

      String idempotencyKey =
          String.format(
              "workout:location-verify:%d:%d:%s",
              verification.getUserId(), verification.getLocationId(), timestamp);

      // Request XP grant to Level module (WORKOUT type)
      int grantedXp =
          grantXpPort.grantLocationVerificationXp(
              verification.getUserId(), verification.getVerifiedAt(), idempotencyKey);

      log.info(
          "XP granted for location verification (WORKOUT): userId={}, xp={}",
          verification.getUserId(),
          grantedXp);

    } catch (Exception e) {
      // XP grant failure does not roll back the entire transaction
      // (verification record must be saved)
      log.error(
          "Failed to grant XP for location verification: userId={}", verification.getUserId(), e);
    }
  }
}
