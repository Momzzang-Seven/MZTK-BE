package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;

/**
 * Input port for retrieving the authenticated user's full profile. Aggregates data from the user,
 * level, attendance, and verification modules into a single response.
 */
public interface GetMyProfileUseCase {

  /**
   * Executes the profile query for the given user.
   *
   * @param userId the ID of the authenticated user
   * @return aggregated profile data
   * @throws momzzangseven.mztkbe.global.error.UserNotFoundException if the user does not exist
   */
  GetMyProfileResult execute(Long userId);
}
