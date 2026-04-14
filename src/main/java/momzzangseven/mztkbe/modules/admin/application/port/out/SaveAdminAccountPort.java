package momzzangseven.mztkbe.modules.admin.application.port.out;

import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;

/** Output port for persisting admin account changes. */
public interface SaveAdminAccountPort {

  AdminAccount save(AdminAccount account);

  /** Saves and immediately flushes to the database, forcing any SQL errors to surface now. */
  AdminAccount saveAndFlush(AdminAccount account);
}
