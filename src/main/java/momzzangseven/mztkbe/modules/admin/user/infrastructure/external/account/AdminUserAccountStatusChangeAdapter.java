package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.account;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusCommand;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusResult;
import momzzangseven.mztkbe.modules.account.application.port.in.UpdateManagedUserAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.ChangeAdminUserAccountStatusPort;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserAccountStatusChangeAdapter implements ChangeAdminUserAccountStatusPort {

  private final UpdateManagedUserAccountStatusUseCase updateManagedUserAccountStatusUseCase;

  @Override
  public ChangeAdminUserAccountStatusResult change(Long userId, AdminUserAccountStatus status) {
    UpdateManagedUserAccountStatusResult result =
        updateManagedUserAccountStatusUseCase.execute(
            new UpdateManagedUserAccountStatusCommand(userId, toAccountStatus(status)));
    return new ChangeAdminUserAccountStatusResult(
        result.userId(), toAdminUserAccountStatus(result.status()));
  }

  private AccountStatus toAccountStatus(AdminUserAccountStatus status) {
    return AccountStatus.valueOf(status.name());
  }

  private AdminUserAccountStatus toAdminUserAccountStatus(AccountStatus status) {
    return AdminUserAccountStatus.valueOf(status.name());
  }
}
