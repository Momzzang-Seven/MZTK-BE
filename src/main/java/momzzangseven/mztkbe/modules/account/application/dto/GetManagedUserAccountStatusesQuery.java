package momzzangseven.mztkbe.modules.account.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.lang.Nullable;

/** Query for managed-user account-status lookups. */
public record GetManagedUserAccountStatusesQuery(
    @Nullable List<Long> userIds, @Nullable AccountStatus statusFilter) {

  public GetManagedUserAccountStatusesQuery {
    if (userIds != null) {
      userIds = List.copyOf(userIds);
    }
  }
}
