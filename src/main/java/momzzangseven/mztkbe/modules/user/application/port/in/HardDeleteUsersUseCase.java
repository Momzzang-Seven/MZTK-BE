package momzzangseven.mztkbe.modules.user.application.port.in;

import java.util.List;

/**
 * Inbound port for hard-deleting user records. Called by the account module's hard-delete batch
 * process after the retention period.
 */
public interface HardDeleteUsersUseCase {

  void hardDeleteUsers(List<Long> userIds);
}
