package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.account;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.GetManagedUserAccountStatusesQuery;
import momzzangseven.mztkbe.modules.account.application.port.in.GetManagedUserAccountStatusesUseCase;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserStatusesPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserStatusAdapter implements LoadAdminUserStatusesPort {

  private final GetManagedUserAccountStatusesUseCase getManagedUserAccountStatusesUseCase;

  @Override
  public Map<Long, AccountStatus> load(List<Long> userIds, AccountStatus statusFilter) {
    return getManagedUserAccountStatusesUseCase.execute(
        new GetManagedUserAccountStatusesQuery(userIds, statusFilter));
  }
}
