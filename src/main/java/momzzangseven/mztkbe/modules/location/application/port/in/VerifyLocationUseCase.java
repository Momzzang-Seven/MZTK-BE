package momzzangseven.mztkbe.modules.location.application.port.in;

import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;

/**
 * Verify Location Use Case
 *
 * <p>Location verification use case. Verify if the user is actually at the registered location
 * using GPS.
 */
public interface VerifyLocationUseCase {
  /**
   * Execute location verification
   *
   * @param command Verification command (locationId, currentCoordinate)
   * @return VerifyLocationResult Verification result
   */
  VerifyLocationResult execute(VerifyLocationCommand command);
}
