package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.List;

/**
 * Output port for deleting user level data during hard-delete. Implemented by an infrastructure
 * adapter that delegates to the level module's {@code DeleteUserLevelDataUseCase}.
 */
public interface DeleteUserLevelDataPort {

  void deleteByUserIds(List<Long> userIds);
}
