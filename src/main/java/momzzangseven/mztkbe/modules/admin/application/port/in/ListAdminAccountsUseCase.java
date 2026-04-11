package momzzangseven.mztkbe.modules.admin.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.admin.application.dto.AdminAccountSummary;

/** Input port for listing all active admin accounts. */
public interface ListAdminAccountsUseCase {

  List<AdminAccountSummary> execute(Long operatorUserId);
}
