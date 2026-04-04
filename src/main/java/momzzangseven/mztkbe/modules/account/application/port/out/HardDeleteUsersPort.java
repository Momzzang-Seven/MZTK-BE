package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.List;

/**
 * Output port for hard-deleting user records. Implemented by an infrastructure adapter that
 * delegates to the user module's {@code HardDeleteUsersUseCase}.
 */
public interface HardDeleteUsersPort {

  void hardDeleteUsers(List<Long> userIds);
}
