package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.List;

/**
 * Output port for hard-deleting {@link
 * momzzangseven.mztkbe.modules.account.domain.model.UserAccount} records.
 */
public interface DeleteUserAccountPort {

  /** Permanently deletes the account record for the given user. */
  void deleteByUserId(Long userId);

  /** Permanently deletes all account records for the given list of user IDs. */
  void deleteByUserIdIn(List<Long> userIds);
}
