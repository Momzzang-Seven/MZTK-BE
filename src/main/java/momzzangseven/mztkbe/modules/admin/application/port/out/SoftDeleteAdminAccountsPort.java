package momzzangseven.mztkbe.modules.admin.application.port.out;

/** Output port for bulk soft-deleting all active admin accounts. */
public interface SoftDeleteAdminAccountsPort {

  /** Soft-delete all active admin accounts. Returns the number of affected rows. */
  int softDeleteAll();
}
