package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import java.util.List;
import java.util.Map;

/** Output port for loading per-user comment counts from the comment module. */
public interface LoadAdminUserCommentCountsPort {

  Map<Long, Long> load(List<Long> userIds);
}
