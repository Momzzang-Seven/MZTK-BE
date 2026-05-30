package momzzangseven.mztkbe.modules.account.infrastructure.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountStatusRegistryPort;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Warms up the in-memory account-status denylist at startup so it is populated before traffic
 * arrives. When the registry is enabled, the auth hot path reads only the denylist; an unloaded
 * denylist would treat every user as ACTIVE (absence = ACTIVE), letting BLOCKED/DELETED/UNVERIFIED
 * users pass authentication. To stay fail-closed, this warm-up <strong>must</strong> succeed.
 *
 * <p>Runs after Flyway migrations ({@link Ordered#LOWEST_PRECEDENCE}, mirroring {@code
 * SeedAdminBootstrapper}). The reconcile use case is itself fail-soft (it logs and swallows so the
 * scheduler thread never dies), so this runner detects success via {@link
 * LoadAccountStatusRegistryPort#isReady()} rather than via an exception. It retries reconcile up to
 * {@value #MAX_ATTEMPTS} times; if the registry is still not ready afterwards it throws, which
 * fails application boot instead of serving traffic against an empty (fail-open) denylist.
 *
 * <p>This bean is only created when {@code account.status-registry.enabled=true} (default), so the
 * boot-fail behavior applies only when the registry is the source of truth for the auth hot path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(
    name = "account.status-registry.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AccountStatusRegistryWarmupRunner implements ApplicationRunner {

  private static final int MAX_ATTEMPTS = 3;
  private static final long WARMUP_RETRY_BACKOFF_MILLIS = 1500L;

  private final ReconcileAccountStatusRegistryUseCase reconcileUseCase;
  private final LoadAccountStatusRegistryPort loadAccountStatusRegistryPort;

  @Override
  public void run(ApplicationArguments args) {
    log.info("AccountStatusRegistryWarmupRunner: warming up account status denylist...");
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      reconcileUseCase.reconcile();
      if (loadAccountStatusRegistryPort.isReady()) {
        log.info(
            "AccountStatusRegistryWarmupRunner: account status denylist warm-up complete "
                + "(attempt {}/{})",
            attempt,
            MAX_ATTEMPTS);
        return;
      }
      log.warn(
          "AccountStatusRegistryWarmupRunner: denylist not ready after attempt {}/{}",
          attempt,
          MAX_ATTEMPTS);
      // Back off between attempts so a transient DB blip can recover; no sleep after the last
      // attempt (we are about to fail boot). An interrupt aborts the loop and falls through to the
      // fail-closed throw below.
      if (attempt < MAX_ATTEMPTS && !sleepBetweenAttempts()) {
        break;
      }
    }
    throw new IllegalStateException(
        "Account status denylist failed to load after "
            + MAX_ATTEMPTS
            + " attempts; refusing to start to avoid a fail-open auth hot path. "
            + "Check DB connectivity, or set account.status-registry.enabled=false to fall back "
            + "to per-request DB status checks.");
  }

  /**
   * Sleeps for the inter-attempt backoff. Returns {@code false} if interrupted (restoring the
   * interrupt flag) so the caller stops retrying and fails boot. Package-private and overridable so
   * unit tests can skip the real sleep.
   */
  boolean sleepBetweenAttempts() {
    try {
      Thread.sleep(WARMUP_RETRY_BACKOFF_MILLIS);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
