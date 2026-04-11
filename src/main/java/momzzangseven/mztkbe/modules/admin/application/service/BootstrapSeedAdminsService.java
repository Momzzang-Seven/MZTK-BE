package momzzangseven.mztkbe.modules.admin.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.dto.SeedBootstrapOutcome;
import momzzangseven.mztkbe.modules.admin.application.port.in.BootstrapSeedAdminsUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.CountActiveAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service that bootstraps seed admin accounts when fewer than 2 exist. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BootstrapSeedAdminsService implements BootstrapSeedAdminsUseCase {

  private static final int REQUIRED_SEED_COUNT = 2;
  private static final String DELIVERY_TARGET = "mztk/admin/bootstrap-delivery";

  private final CountActiveAdminAccountsPort countActiveAdminAccountsPort;
  private final SeedProvisioner seedProvisioner;
  private final BootstrapDeliveryPort bootstrapDeliveryPort;

  @Override
  @Transactional
  public SeedBootstrapOutcome execute() {
    long currentCount = countActiveAdminAccountsPort.countActive();
    if (currentCount >= REQUIRED_SEED_COUNT) {
      log.info("Seed bootstrap skipped: {} active admin(s) already exist", currentCount);
      return new SeedBootstrapOutcome(false, (int) currentCount, null);
    }

    int deficit = (int) (REQUIRED_SEED_COUNT - currentCount);
    List<GeneratedAdminCredentials> credentials =
        seedProvisioner.provision(deficit, AdminRole.ADMIN_SEED);

    bootstrapDeliveryPort.deliver(credentials);

    log.info(
        "Seed bootstrap completed: {} admin(s) created, delivered to {}", deficit, DELIVERY_TARGET);
    return new SeedBootstrapOutcome(true, deficit, DELIVERY_TARGET);
  }
}
