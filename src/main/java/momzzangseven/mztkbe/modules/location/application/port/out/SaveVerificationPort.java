package momzzangseven.mztkbe.modules.location.application.port.out;

import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;

/**
 * Save Verification Port
 *
 * <p>Location verification record save port.
 */
public interface SaveVerificationPort {
  /**
   * Save verification record
   *
   * @param verification Verification domain model
   * @return Saved verification record (ID included)
   */
  LocationVerification save(LocationVerification verification);
}
