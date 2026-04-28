package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;

/**
 * Transitions a wallet ACTIVE → DISABLED, persists, then asks KMS to disable the backing key.
 * Audit entries are recorded in a separate transaction so the audit trail survives a failure of
 * the KMS step.
 *
 * <p>Skeleton — not yet registered as a Spring bean. {@code @Service} / {@code @Transactional}
 * annotations and the {@code REQUIRES_NEW} audit propagation land in commit 1-10 once the lifecycle
 * adapter exists.
 */
@Slf4j
@RequiredArgsConstructor
public class DisableTreasuryWalletService implements DisableTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;
  private final Clock clock;

  @Override
  public TreasuryWalletView execute(DisableTreasuryWalletCommand command) {
    TreasuryWallet wallet =
        loadTreasuryWalletPort
            .loadByAlias(command.walletAlias())
            .orElseThrow(
                () ->
                    new TreasuryWalletStateException(
                        "Treasury wallet '" + command.walletAlias() + "' not found"));

    String walletAddress = wallet.getWalletAddress();
    try {
      TreasuryWallet disabled = wallet.disable(clock);
      TreasuryWallet saved = saveTreasuryWalletPort.save(disabled);
      kmsKeyLifecyclePort.disableKey(saved.getKmsKeyId());
      recordAudit(command.operatorUserId(), walletAddress, true, null);
      return TreasuryWalletView.from(saved);
    } catch (RuntimeException e) {
      recordAudit(command.operatorUserId(), walletAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }

  void recordAudit(Long operatorId, String walletAddress, boolean success, String failureReason) {
    try {
      recordTreasuryProvisionAuditPort.record(
          new RecordTreasuryProvisionAuditPort.AuditCommand(
              operatorId, walletAddress, success, failureReason));
    } catch (Exception e) {
      log.warn(
          "Failed to record disable-treasury-wallet audit: operatorId={}, success={}",
          operatorId,
          success,
          e);
    }
  }
}
