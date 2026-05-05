package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import org.springframework.lang.Nullable;

/** Output port for loading managed-user account statuses from the account module. */
public interface LoadAdminUserStatusesPort {

  Map<Long, AdminUserAccountStatus> load(
      @Nullable List<Long> userIds, @Nullable AdminUserAccountStatus statusFilter);
}
