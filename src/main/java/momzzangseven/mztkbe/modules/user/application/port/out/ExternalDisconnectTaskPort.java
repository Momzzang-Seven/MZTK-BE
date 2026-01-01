package momzzangseven.mztkbe.modules.user.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;

public interface ExternalDisconnectTaskPort {
  List<ExternalDisconnectTask> findDueTasks(LocalDateTime now, int limit);

  ExternalDisconnectTask save(ExternalDisconnectTask task);

  int deleteByUserIdIn(List<Long> userIds);
}

