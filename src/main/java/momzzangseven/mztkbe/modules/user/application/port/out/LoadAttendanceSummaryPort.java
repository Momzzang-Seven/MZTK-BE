package momzzangseven.mztkbe.modules.user.application.port.out;

import momzzangseven.mztkbe.modules.user.application.dto.AttendanceSummary;

/**
 * Output port for loading a user's attendance summary. Implemented by an infrastructure adapter
 * that delegates to the level module's attendance use cases.
 */
public interface LoadAttendanceSummaryPort {

  /**
   * Loads today's attendance status and the weekly attendance count for the given user.
   *
   * @param userId the user's ID
   * @return the user's attendance summary
   */
  AttendanceSummary loadSummary(Long userId);
}
