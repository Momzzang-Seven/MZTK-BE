package momzzangseven.mztkbe.modules.admin.application.port.in;

import momzzangseven.mztkbe.modules.admin.application.dto.CreateAdminAccountResult;

/** Input port for creating a new admin account. */
public interface CreateAdminAccountUseCase {

  CreateAdminAccountResult execute(Long operatorUserId);
}
