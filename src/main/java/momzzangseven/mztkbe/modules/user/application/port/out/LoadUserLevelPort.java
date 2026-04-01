package momzzangseven.mztkbe.modules.user.application.port.out;

import momzzangseven.mztkbe.modules.user.application.dto.UserLevelInfo;

/**
 * Output port for loading a user's current level and XP information. Implemented by an
 * infrastructure adapter that delegates to the level module.
 */
public interface LoadUserLevelPort {

  /**
   * Loads level and XP data for the given user.
   *
   * @param userId the user's ID
   * @return the user's level and XP snapshot
   */
  UserLevelInfo loadLevelInfo(Long userId);
}
