package momzzangseven.mztkbe.modules.account.infrastructure.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Warms up the in-memory account-status denylist at startup so it is populated before traffic
 * arrives. An empty denylist means "no blocked users" (fail-open, §4), so this warm-up is required.
 *
 * <p>Runs after Flyway migrations ({@link Ordered#LOWEST_PRECEDENCE}, mirroring {@code
 * SeedAdminBootstrapper}). The reconcile use case is fail-soft (it logs and swallows internally),
 * so a failed warm-up never crashes startup — the next scheduled reconcile will retry.
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

  private final ReconcileAccountStatusRegistryUseCase reconcileUseCase;

  @Override
  public void run(ApplicationArguments args) {
    log.info("AccountStatusRegistryWarmupRunner: warming up account status denylist...");
    reconcileUseCase.reconcile();
    log.info("AccountStatusRegistryWarmupRunner: account status denylist warm-up complete");
  }
}
