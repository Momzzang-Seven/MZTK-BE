package momzzangseven.mztkbe.modules.user.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;

/** Output port for reading external disconnect tasks due for execution. */
public interface LoadExternalDisconnectTaskPort {
  List<ExternalDisconnectTask> findDueTasks(LocalDateTime now, int limit);
}
