package momzzangseven.mztkbe.modules.admin.infrastructure.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.dto.SeedBootstrapOutcome;
import momzzangseven.mztkbe.modules.admin.application.port.in.BootstrapSeedAdminsUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Application startup component that ensures seed admin accounts exist. Runs after Flyway
 * migrations (LOWEST_PRECEDENCE). Disabled by setting {@code mztk.admin.bootstrap.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(
    name = "mztk.admin.bootstrap.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SeedAdminBootstrapper implements ApplicationRunner {

  private final BootstrapSeedAdminsUseCase bootstrapSeedAdminsUseCase;
  private final RecoveryAnchorPort recoveryAnchorPort;

  @Override
  public void run(ApplicationArguments args) {
    log.info("SeedAdminBootstrapper: verifying recovery anchor accessibility...");
    try {
      String anchor = recoveryAnchorPort.loadAnchor();
      if (anchor == null || anchor.isBlank()) {
        throw new IllegalStateException("Recovery anchor is empty — cannot proceed with bootstrap");
      }
      log.info("SeedAdminBootstrapper: recovery anchor verified");
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Recovery anchor unavailable — aborting bootstrap (fail-fast)", e);
    }

    SeedBootstrapOutcome outcome = bootstrapSeedAdminsUseCase.execute();

    if (outcome.created()) {
      log.info(
          "SeedAdminBootstrapper: {} seed admin(s) created, delivered to {}",
          outcome.seedCount(),
          outcome.deliveredVia());
    } else {
      log.info(
          "SeedAdminBootstrapper: no-op, {} active admin(s) already present", outcome.seedCount());
    }
  }
}
