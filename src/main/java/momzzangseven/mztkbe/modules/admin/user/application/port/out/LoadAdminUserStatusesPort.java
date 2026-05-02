package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.lang.Nullable;

/** Output port for loading managed-user account statuses from the account module. */
public interface LoadAdminUserStatusesPort {

  Map<Long, AccountStatus> load(@Nullable List<Long> userIds, @Nullable AccountStatus statusFilter);
}
