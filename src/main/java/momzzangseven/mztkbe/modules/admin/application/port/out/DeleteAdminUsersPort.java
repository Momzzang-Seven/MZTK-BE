package momzzangseven.mztkbe.modules.admin.application.port.out;

import java.util.List;

/** Output port for hard-deleting user records associated with admin accounts. */
public interface DeleteAdminUsersPort {

  /**
   * Hard-delete user records by their IDs.
   *
   * @param userIds the IDs of the users to delete
   */
  void deleteUsers(List<Long> userIds);
}
