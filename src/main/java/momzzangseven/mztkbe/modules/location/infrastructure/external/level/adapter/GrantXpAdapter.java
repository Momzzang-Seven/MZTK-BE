package momzzangseven.mztkbe.modules.location.infrastructure.external.level.adapter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.location.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import org.springframework.stereotype.Component;

/**
 * Grant XP Adapter
 *
 * <p>Location module → Level module XP grant adapter.
 *
 * <p>Hexagonal Architecture Outbound Adapter, call GrantXpUseCase from Level module.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantXpAdapter implements GrantXpPort {
  private final GrantXpUseCase grantXpUseCase; // Level module's UseCase
  private final ZoneId appZoneId;
  private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

  @Override
  public int grantLocationVerificationXp(LocationVerification verification) {
    // Required parameters Initialization
    Long userId = verification.getUserId();
    Long locationId = verification.getLocationId();
    LocalDateTime occurredAt = LocalDateTime.ofInstant(verification.getVerifiedAt(), appZoneId);
    String idempotencyKey =
        "workout:location-verify:"
            + userId
            + ":"
            + locationId
            + ":"
            + occurredAt.format(
                YYYYMMDD); // additional prefix to describe this WORKOUT GrantXp type is especially
    // for location verification
    String sourceRef = "location-verification:" + verification.getLocationId();

    log.debug("Granting XP for location verification: userId={}, key={}", userId, idempotencyKey);

    // Call GrantXpUseCase from Level module
    // Business rule: location verification = workout verification (WORKOUT type used)
    GrantXpCommand command =
        GrantXpCommand.of(userId, XpType.WORKOUT, occurredAt, idempotencyKey, sourceRef);

    GrantXpResult result = grantXpUseCase.execute(command);

    if (result.grantedXp() > 0) {
      log.info(
          "XP granted for location verification (WORKOUT): userId={}, xp={}",
          userId,
          result.grantedXp());
    } else {
      log.info(
          "XP not granted: userId={}, reason={}",
          userId,
          result.status().equals(GrantXpResult.Status.DAILY_CAP_REACHED)
              ? "daily cap reached"
              : "already granted");
    }

    return result.grantedXp();
  }
}
