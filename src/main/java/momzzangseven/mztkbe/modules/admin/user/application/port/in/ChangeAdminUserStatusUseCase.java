package momzzangseven.mztkbe.modules.admin.user.application.port.in;

import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusCommand;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusResult;

/** Input port for changing a managed user's account status. */
public interface ChangeAdminUserStatusUseCase {

  ChangeAdminUserStatusResult execute(ChangeAdminUserStatusCommand command);
}
