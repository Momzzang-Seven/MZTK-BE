package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.stereotype.Service;

/**
 * Resolves account status for the auth layer over two distinct paths (MOM-464).
 *
 * <p><strong>Hot path</strong> — the predicate methods ({@link #isActive}, {@link #isDeleted},
 * {@link #isBlocked}) read the in-memory denylist via {@link LoadAccountStatusRegistryPort},
 * acquiring <strong>zero HikariCP connections</strong>. Only non-ACTIVE users are tracked, so an
 * absent userId is interpreted as ACTIVE (absence = ACTIVE). This is the deliberate negative-cache
 * model: the JWT filter trusts a validly-signed token unless the user is in the non-ACTIVE
 * denylist.
 *
 * <p><strong>Cold path</strong> — {@link #findStatus} stays on the DB via {@link
 * LoadUserAccountPort} because the reissue flow must distinguish {@code Optional.empty()} (user
 * absent) from a real status, which the denylist's absence=ACTIVE semantics cannot express. Reissue
 * is low-frequency, so a single connection there is harmless.
 *
 * <p>Denylist eviction on invalidation/hard-delete is handled by {@code
 * AccountStatusRegistryEventHandler}; this service no longer listens to events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckAccountStatusService implements CheckAccountStatusUseCase {

  private final LoadAccountStatusRegistryPort loadAccountStatusRegistryPort;
  private final LoadUserAccountPort loadUserAccountPort;

  @Override
  public boolean isActive(Long userId) {
    return loadAccountStatusRegistryPort.statusOf(userId) == AccountStatus.ACTIVE;
  }

  @Override
  public boolean isDeleted(Long userId) {
    return loadAccountStatusRegistryPort.statusOf(userId) == AccountStatus.DELETED;
  }

  @Override
  public boolean isBlocked(Long userId) {
    return loadAccountStatusRegistryPort.statusOf(userId) == AccountStatus.BLOCKED;
  }

  @Override
  public Optional<AccountStatus> findStatus(Long userId) {
    return loadUserAccountPort.findByUserId(userId).map(UserAccount::getStatus);
  }
}
