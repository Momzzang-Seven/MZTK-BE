package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusCommand;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusResult;

/** Input port for admin-managed account status changes. */
public interface UpdateManagedUserAccountStatusUseCase {

  UpdateManagedUserAccountStatusResult execute(UpdateManagedUserAccountStatusCommand command);
}
