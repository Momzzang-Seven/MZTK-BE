package momzzangseven.mztkbe.modules.admin.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.event.AdminAccountInvalidatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Caches admin-active flag by {@code userId} so the JWT filter and reissue path skip the
 * AdminAccount lookup for already-resolved admins. Same inline-Caffeine pattern as {@link
 * momzzangseven.mztkbe.modules.account.application.service.CheckAccountStatusService}.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class CheckAdminAccountStatusService implements CheckAdminAccountStatusUseCase {

  private static final Duration CACHE_TTL = Duration.ofSeconds(60);
  private static final long CACHE_MAXIMUM_SIZE = 1_000L;

  private final LoadAdminAccountPort loadAdminAccountPort;
  private final Cache<Long, Boolean> activeCache;

  public CheckAdminAccountStatusService(LoadAdminAccountPort loadAdminAccountPort) {
    this.loadAdminAccountPort = loadAdminAccountPort;
    this.activeCache =
        Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).maximumSize(CACHE_MAXIMUM_SIZE).build();
  }

  @Override
  public boolean isActiveAdmin(Long userId) {
    return activeCache.get(userId, id -> loadAdminAccountPort.findActiveByUserId(id).isPresent());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onAdminAccountInvalidated(AdminAccountInvalidatedEvent event) {
    activeCache.invalidate(event.userId());
    log.debug("AdminAccount active cache invalidated: userId={}", event.userId());
  }
}
