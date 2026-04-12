package momzzangseven.mztkbe.modules.admin.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;

/** Output port for reading admin account data from persistence. */
public interface LoadAdminAccountPort {

  Optional<AdminAccount> findActiveByUserId(Long userId);

  Optional<AdminAccount> findActiveByLoginId(String loginId);

  /** Loads an active admin account by loginId with a pessimistic write lock. */
  Optional<AdminAccount> findActiveByLoginIdForUpdate(String loginId);

  boolean existsByLoginId(String loginId);

  List<AdminAccount> findAllActive();
}
