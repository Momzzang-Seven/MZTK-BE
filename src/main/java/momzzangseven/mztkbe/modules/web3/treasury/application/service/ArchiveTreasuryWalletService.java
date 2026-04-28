package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ArchiveTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;

/**
 * Transitions DISABLED → ARCHIVED, persists, and schedules the backing KMS key for permanent
 * deletion. The default 30-day pending window matches the KMS minimum and gives operators a
 * recovery buffer before the key material is destroyed.
 *
 * <p>Skeleton — not yet registered as a Spring bean. {@code @Service} / {@code @Transactional}
 * annotations land in commit 1-10 once the lifecycle adapter exists.
 */
@Slf4j
@RequiredArgsConstructor
public class ArchiveTreasuryWalletService implements ArchiveTreasuryWalletUseCase {

  /** Pending window passed to {@code KmsKeyLifecyclePort.scheduleKeyDeletion} on archive. */
  static final int DEFAULT_KMS_PENDING_WINDOW_DAYS = 30;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;
  private final Clock clock;

  @Override
  public TreasuryWalletView execute(ArchiveTreasuryWalletCommand command) {
    TreasuryWallet wallet =
        loadTreasuryWalletPort
            .loadByAlias(command.walletAlias())
            .orElseThrow(
                () ->
                    new TreasuryWalletStateException(
                        "Treasury wallet '" + command.walletAlias() + "' not found"));

    String walletAddress = wallet.getWalletAddress();
    try {
      TreasuryWallet archived = wallet.archive(clock);
      TreasuryWallet saved = saveTreasuryWalletPort.save(archived);
      kmsKeyLifecyclePort.scheduleKeyDeletion(
          saved.getKmsKeyId(), DEFAULT_KMS_PENDING_WINDOW_DAYS);
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
          "Failed to record archive-treasury-wallet audit: operatorId={}, success={}",
          operatorId,
          success,
          e);
    }
  }
}
