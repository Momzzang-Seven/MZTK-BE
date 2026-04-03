package momzzangseven.mztkbe.modules.account.application.port.out;

import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;

/** Output port for persisting {@link UserAccount} state changes. */
public interface SaveUserAccountPort {

  /** Saves a new or updated account and returns the persisted result. */
  UserAccount save(UserAccount userAccount);
}
