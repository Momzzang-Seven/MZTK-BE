package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.lang.Nullable;

/** Output port for bulk managed-user account-status reads. */
public interface LoadManagedUserAccountStatusesPort {

  Map<Long, AccountStatus> load(@Nullable List<Long> userIds, @Nullable AccountStatus statusFilter);
}
