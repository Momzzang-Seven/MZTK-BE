package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.account;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusCommand;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusResult;
import momzzangseven.mztkbe.modules.account.application.port.in.UpdateManagedUserAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.ChangeAdminUserAccountStatusPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserAccountStatusChangeAdapter implements ChangeAdminUserAccountStatusPort {

  private final UpdateManagedUserAccountStatusUseCase updateManagedUserAccountStatusUseCase;

  @Override
  public ChangeAdminUserAccountStatusResult change(Long userId, AccountStatus status) {
    UpdateManagedUserAccountStatusResult result =
        updateManagedUserAccountStatusUseCase.execute(
            new UpdateManagedUserAccountStatusCommand(userId, status));
    return new ChangeAdminUserAccountStatusResult(result.userId(), result.status());
  }
}
