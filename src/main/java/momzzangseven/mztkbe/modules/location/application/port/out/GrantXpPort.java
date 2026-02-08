package momzzangseven.mztkbe.modules.location.application.port.out;

import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;

/**
 * Grant XP Port
 *
 * <p>XP grant port. Location module → Level module integration.
 */
public interface GrantXpPort {

  /**
   * Grant XP when location verification is successful
   *
   * @param verification
   * @return
   */
  int grantLocationVerificationXp(LocationVerification verification);
}
