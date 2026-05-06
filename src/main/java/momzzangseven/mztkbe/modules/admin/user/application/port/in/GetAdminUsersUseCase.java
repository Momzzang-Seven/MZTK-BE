package momzzangseven.mztkbe.modules.admin.user.application.port.in;

import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserListItemResult;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import org.springframework.data.domain.Page;

public interface GetAdminUsersUseCase {

  Page<AdminUserListItemResult> execute(GetAdminUsersCommand command);
}
