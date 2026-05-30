package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Optional;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves account status for the auth layer over two distinct paths (MOM-464).
 *
 * <p><strong>Hot path</strong> — the predicate methods ({@link #isActive}, {@link #isDeleted},
 * {@link #isBlocked}) resolve the effective status through {@link #resolveStatus}, whose routing
 * depends on {@code account.status-registry.enabled}:
 *
 * <ul>
 *   <li><strong>enabled=true</strong> (default): read the in-memory denylist via {@link
 *       LoadAccountStatusRegistryPort}, acquiring <strong>zero HikariCP connections</strong>. Only
 *       non-ACTIVE users are tracked, so an absent userId is interpreted as ACTIVE (absence =
 *       ACTIVE) — the deliberate negative-cache model. The warm-up runner fails boot if the
 *       denylist never loads, so the hot path never reads an empty (fail-open) denylist.
 *   <li><strong>enabled=false</strong>: fall back to the original pre-MOM-464 behavior and read the
 *       DB via {@link LoadUserAccountPort}. A missing account maps to ACTIVE, mirroring the
 *       denylist's absence=ACTIVE semantics. Never read the (intentionally unpopulated) denylist
 *       while disabled.
 * </ul>
 *
 * <p><strong>Cold path</strong> — {@link #findStatus} always stays on the DB via {@link
 * LoadUserAccountPort} because the reissue flow must distinguish {@code Optional.empty()} (user
 * absent) from a real status, which the denylist's absence=ACTIVE semantics cannot express. Reissue
 * is low-frequency, so a single connection there is harmless.
 *
 * <p>Denylist eviction on invalidation/hard-delete is handled by {@code
 * AccountStatusRegistryEventHandler}; this service no longer listens to events.
 */
@Service
public class CheckAccountStatusService implements CheckAccountStatusUseCase {

  private final LoadAccountStatusRegistryPort loadAccountStatusRegistryPort;
  private final LoadUserAccountPort loadUserAccountPort;
  private final boolean registryEnabled;

  public CheckAccountStatusService(
      LoadAccountStatusRegistryPort loadAccountStatusRegistryPort,
      LoadUserAccountPort loadUserAccountPort,
      @Value("${account.status-registry.enabled:true}") boolean registryEnabled) {
    this.loadAccountStatusRegistryPort = loadAccountStatusRegistryPort;
    this.loadUserAccountPort = loadUserAccountPort;
    this.registryEnabled = registryEnabled;
  }

  @Override
  public boolean isActive(Long userId) {
    return resolveStatus(userId) == AccountStatus.ACTIVE;
  }

  @Override
  public boolean isDeleted(Long userId) {
    return resolveStatus(userId) == AccountStatus.DELETED;
  }

  @Override
  public boolean isBlocked(Long userId) {
    return resolveStatus(userId) == AccountStatus.BLOCKED;
  }

  @Override
  public Optional<AccountStatus> findStatus(Long userId) {
    return loadUserAccountPort.findByUserId(userId).map(UserAccount::getStatus);
  }

  /**
   * Resolves the effective {@link AccountStatus} for the auth hot path, routing to the in-memory
   * denylist when the registry is enabled, or to the DB (absence = ACTIVE) when it is disabled.
   */
  private AccountStatus resolveStatus(Long userId) {
    if (registryEnabled) {
      return loadAccountStatusRegistryPort.statusOf(userId);
    }
    return loadUserAccountPort
        .findByUserId(userId)
        .map(UserAccount::getStatus)
        .orElse(AccountStatus.ACTIVE);
  }
}
