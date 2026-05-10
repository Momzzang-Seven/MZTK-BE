package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import java.util.List;
import java.util.Map;

/** Output port for loading per-user post counts from the post module. */
public interface LoadAdminUserPostCountsPort {

  Map<Long, Long> load(List<Long> userIds);
}
