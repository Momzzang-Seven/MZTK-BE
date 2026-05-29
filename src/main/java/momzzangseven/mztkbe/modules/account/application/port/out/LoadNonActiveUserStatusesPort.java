package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/** Output port for loading a full snapshot of every non-ACTIVE user account status. */
public interface LoadNonActiveUserStatusesPort {

  /**
   * Returns a snapshot map of every non-ACTIVE user (userId → status). Used by reconcile/warmup to
   * rebuild the in-memory denylist. Returns an empty map when no non-ACTIVE account exists.
   *
   * @return map of userId to its non-ACTIVE {@link AccountStatus}; empty if none
   */
  Map<Long, AccountStatus> loadAllNonActive();
}
