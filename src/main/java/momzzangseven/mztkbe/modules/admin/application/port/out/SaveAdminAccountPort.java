package momzzangseven.mztkbe.modules.admin.application.port.out;

import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;

/** Output port for persisting admin account changes. */
public interface SaveAdminAccountPort {

  AdminAccount save(AdminAccount account);
}
