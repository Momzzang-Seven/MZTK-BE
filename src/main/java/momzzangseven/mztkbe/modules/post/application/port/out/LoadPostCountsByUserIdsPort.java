package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Map;

/** Output port for bulk post-count aggregation by author user id. */
public interface LoadPostCountsByUserIdsPort {

  Map<Long, Long> load(List<Long> userIds);
}
