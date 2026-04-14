package momzzangseven.mztkbe.modules.admin.application.service;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.admin.RecoveryAnchorUnavailableException;
import momzzangseven.mztkbe.global.error.admin.RecoveryRejectedException;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.RecoveryReseedUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.DeleteAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.DeleteAdminUsersPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadSeedPolicyPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service implementing the break-glass recovery reseed operation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryReseedService implements RecoveryReseedUseCase {

  private final LoadSeedPolicyPort loadSeedPolicyPort;
  private final RecoveryAnchorPort recoveryAnchorPort;
  private final DeleteAdminAccountsPort deleteAdminAccountsPort;
  private final DeleteAdminUsersPort deleteAdminUsersPort;
  private final SeedProvisioner seedProvisioner;
  private final BootstrapDeliveryPort bootstrapDeliveryPort;
  private final RecordAdminAuditPort recordAdminAuditPort;

  @Override
  @Transactional
  public RecoveryReseedResult execute(RecoveryReseedCommand command) {
    String storedAnchor;
    try {
      storedAnchor = recoveryAnchorPort.loadAnchor();
    } catch (Exception e) {
      throw new RecoveryAnchorUnavailableException("Cannot load recovery anchor", e);
    }

    if (!MessageDigest.isEqual(command.rawAnchor().getBytes(), storedAnchor.getBytes())) {
      recordAudit("RECOVERY_REJECTED", command.sourceIp(), false);
      throw new RecoveryRejectedException();
    }

    List<Long> deletedUserIds = deleteAdminAccountsPort.deleteAllAndReturnUserIds();
    log.info("Recovery: hard-deleted {} existing admin accounts", deletedUserIds.size());
    deleteAdminUsersPort.deleteUsers(deletedUserIds);
    log.info("Recovery: hard-deleted {} user records for admin accounts", deletedUserIds.size());

    int seedCount = loadSeedPolicyPort.getSeedCount();
    List<GeneratedAdminCredentials> credentials =
        seedProvisioner.provision(seedCount, AdminRole.ADMIN_SEED);

    bootstrapDeliveryPort.deliver(credentials);

    recordAudit("RECOVERY_SUCCESS", command.sourceIp(), true);
    log.info("Recovery reseed completed: {} new seed admins created", seedCount);

    return new RecoveryReseedResult(seedCount, loadSeedPolicyPort.getDeliveryTarget());
  }

  private void recordAudit(String actionType, String sourceIp, boolean success) {
    try {
      recordAdminAuditPort.record(
          new RecordAdminAuditPort.AuditCommand(
              null,
              actionType,
              AuditTargetType.ADMIN_ACCOUNT,
              null,
              success,
              Map.of("sourceIp", sourceIp != null ? sourceIp : "unknown")));
    } catch (Exception e) {
      log.warn("Failed to record recovery audit: {}", e.getMessage());
    }
  }
}
