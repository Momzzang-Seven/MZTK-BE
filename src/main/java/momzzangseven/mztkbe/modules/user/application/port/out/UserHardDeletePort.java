package momzzangseven.mztkbe.modules.user.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;

public interface UserHardDeletePort {
  List<Long> loadUserIdsForHardDelete(UserStatus status, LocalDateTime cutoff, int limit);

  void deleteAllByIdInBatch(List<Long> userIds);
}
