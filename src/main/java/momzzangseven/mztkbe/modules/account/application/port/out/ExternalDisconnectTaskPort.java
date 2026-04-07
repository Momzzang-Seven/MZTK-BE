package momzzangseven.mztkbe.modules.account.application.port.out;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectTask;

/** Output port for managing external disconnect tasks. */
public interface ExternalDisconnectTaskPort {

  List<ExternalDisconnectTask> findDueTasks(Instant now, int limit);

  ExternalDisconnectTask save(ExternalDisconnectTask task);

  int deleteByUserIdIn(List<Long> userIds);

  /** Delete completed/failed tasks older than the cutoff. */
  int deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus status, Instant cutoff);
}
