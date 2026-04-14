package momzzangseven.mztkbe.modules.admin.application.port.out;

import java.util.List;

/** Output port for hard-deleting all admin accounts during recovery reseed. */
public interface DeleteAdminAccountsPort {

  /**
   * Hard-delete all admin accounts and return the user IDs that were associated with them.
   *
   * @return list of user IDs whose admin accounts were deleted
   */
  List<Long> deleteAllAndReturnUserIds();
}
