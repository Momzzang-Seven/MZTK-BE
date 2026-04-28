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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions a wallet ACTIVE → DISABLED, persists, then asks KMS to disable the backing key.
 * Audit entries are recorded in {@link Propagation#REQUIRES_NEW} so the audit trail survives a
 * failure of the KMS step.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisableTreasuryWalletService implements DisableTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;
  private final Clock clock;

  @Override
  @Transactional
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
