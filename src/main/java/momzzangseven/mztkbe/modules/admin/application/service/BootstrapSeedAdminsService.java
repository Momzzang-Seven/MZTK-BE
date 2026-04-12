package momzzangseven.mztkbe.modules.admin.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.dto.SeedBootstrapOutcome;
import momzzangseven.mztkbe.modules.admin.application.port.in.BootstrapSeedAdminsUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.CountActiveAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadSeedPolicyPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service that bootstraps seed admin accounts when fewer than 2 exist. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BootstrapSeedAdminsService implements BootstrapSeedAdminsUseCase {

  private final LoadSeedPolicyPort loadSeedPolicyPort;
  private final CountActiveAdminAccountsPort countActiveAdminAccountsPort;
  private final SeedProvisioner seedProvisioner;
  private final BootstrapDeliveryPort bootstrapDeliveryPort;

  @Override
  @Transactional
  public SeedBootstrapOutcome execute() {
    int requiredSeedCount = loadSeedPolicyPort.getSeedCount();
    long currentCount = countActiveAdminAccountsPort.countActiveByRole(AdminRole.ADMIN_SEED.name());
    if (currentCount >= requiredSeedCount) {
      log.info("Seed bootstrap skipped: {} active seed admin(s) already exist", currentCount);
      return new SeedBootstrapOutcome(false, (int) currentCount, null);
    }

    int deficit = (int) (requiredSeedCount - currentCount);
    List<GeneratedAdminCredentials> credentials =
        seedProvisioner.provision(deficit, AdminRole.ADMIN_SEED);

    bootstrapDeliveryPort.deliver(credentials);

    String deliveryTarget = loadSeedPolicyPort.getDeliveryTarget();
    log.info(
        "Seed bootstrap completed: {} admin(s) created, delivered to {}", deficit, deliveryTarget);
    return new SeedBootstrapOutcome(true, deficit, deliveryTarget);
  }
}
