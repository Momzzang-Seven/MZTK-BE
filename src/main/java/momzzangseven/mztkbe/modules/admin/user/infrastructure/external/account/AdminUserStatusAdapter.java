package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.account;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.GetManagedUserAccountStatusesQuery;
import momzzangseven.mztkbe.modules.account.application.port.in.GetManagedUserAccountStatusesUseCase;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserStatusesPort;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserStatusAdapter implements LoadAdminUserStatusesPort {

  private final GetManagedUserAccountStatusesUseCase getManagedUserAccountStatusesUseCase;

  @Override
  public Map<Long, AdminUserAccountStatus> load(
      List<Long> userIds, AdminUserAccountStatus statusFilter) {
    return getManagedUserAccountStatusesUseCase
        .execute(new GetManagedUserAccountStatusesQuery(userIds, toAccountStatus(statusFilter)))
        .entrySet()
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> toAdminUserAccountStatus(entry.getValue())));
  }

  private AccountStatus toAccountStatus(AdminUserAccountStatus status) {
    return status == null ? null : AccountStatus.valueOf(status.name());
  }

  private AdminUserAccountStatus toAdminUserAccountStatus(AccountStatus status) {
    return AdminUserAccountStatus.valueOf(status.name());
  }
}
