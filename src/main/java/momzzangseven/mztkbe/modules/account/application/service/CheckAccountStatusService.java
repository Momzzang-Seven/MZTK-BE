package momzzangseven.mztkbe.modules.account.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.event.UserAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Caches account status by {@code userId} so the JWT filter and reissue path don't take a HikariCP
 * connection per request. Follows the inline-Caffeine pattern of {@code DescribeKmsKeyService} —
 * cache invariant lives entirely in this class.
 *
 * <p>Invalidation is driven by {@link UserAccountInvalidatedEvent} via {@code AFTER_COMMIT}, so a
 * rolled-back write leaves the cache untouched (mirrors {@code
 * [[feedback_treasury_save_first_ordering]]}).
 *
 * <p>Stale window upper bound = {@link #CACHE_TTL} (60s) for changes published by other instances;
 * for changes from this instance the window is approximately the listener's invoke latency.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class CheckAccountStatusService implements CheckAccountStatusUseCase {

  private static final Duration CACHE_TTL = Duration.ofSeconds(60);
  private static final long CACHE_MAXIMUM_SIZE = 10_000L;

  private final LoadUserAccountPort loadUserAccountPort;
  private final Cache<Long, Optional<AccountStatus>> statusCache;

  public CheckAccountStatusService(LoadUserAccountPort loadUserAccountPort) {
    this.loadUserAccountPort = loadUserAccountPort;
    this.statusCache =
        Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).maximumSize(CACHE_MAXIMUM_SIZE).build();
  }

  @Override
  public boolean isActive(Long userId) {
    return loadStatus(userId).map(s -> s == AccountStatus.ACTIVE).orElse(false);
  }

  @Override
  public boolean isDeleted(Long userId) {
    return loadStatus(userId).map(s -> s == AccountStatus.DELETED).orElse(false);
  }

  @Override
  public boolean isBlocked(Long userId) {
    return loadStatus(userId).map(s -> s == AccountStatus.BLOCKED).orElse(false);
  }

  /**
   * Drops the cached entry for {@code userId} once the publishing write transaction commits. Kept
   * non-transactional ({@link Propagation#NOT_SUPPORTED}) so the listener never takes a HikariCP
   * connection just to invalidate an in-memory entry.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onUserAccountInvalidated(UserAccountInvalidatedEvent event) {
    statusCache.invalidate(event.userId());
    log.debug("UserAccount status cache invalidated: userId={}", event.userId());
  }

  private Optional<AccountStatus> loadStatus(Long userId) {
    return statusCache.get(
        userId, id -> loadUserAccountPort.findByUserId(id).map(UserAccount::getStatus));
  }
}
