package momzzangseven.mztkbe.modules.location.application.port.out;

import java.time.Instant;

/**
 * Grant XP Port
 *
 * <p>XP grant port. Location module → Level module integration.
 */
public interface GrantXpPort {
  /**
   * Grant XP when location verification is successful
   *
   * @param userId User ID
   * @param verifiedAt Verification time
   * @param idempotencyKey Idempotency key (prevent duplicate)
   * @return Granted XP amount
   */
  int grantLocationVerificationXp(Long userId, Instant verifiedAt, String idempotencyKey);
}
