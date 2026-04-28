package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ArchiveTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions DISABLED → ARCHIVED, persists, and schedules the backing KMS key for permanent
 * deletion. The default 30-day pending window matches the KMS minimum and gives operators a
 * recovery buffer before the key material is destroyed.
 *
 * <p>Audit writes go through {@link TreasuryAuditRecorder} (separate bean) so they run in {@code
 * REQUIRES_NEW} and survive a rollback of the outer transaction. An inline
 * {@code @Transactional(REQUIRES_NEW)} method on this same class would not work — Spring AOP cannot
 * intercept self-invocation, so the propagation hint would be silently dropped.
 *
 * <p><b>Save-first ordering</b> — DB save commits before the KMS {@code ScheduleKeyDeletion} call.
 * This intentionally diverges from the design doc §4-4 KMS-first sequence: save-first leaves a
 * recoverable state on KMS failure (ARCHIVED row + still-live KMS key, retryable), while KMS-first
 * would leave the DB unchanged and the KMS key invisibly scheduled for deletion, which is much
 * harder to recover from.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArchiveTreasuryWalletService implements ArchiveTreasuryWalletUseCase {

  /** Pending window passed to {@code KmsKeyLifecyclePort.scheduleKeyDeletion} on archive. */
  static final int DEFAULT_KMS_PENDING_WINDOW_DAYS = 30;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final Clock clock;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "TREASURY_KEY_ARCHIVE",
      targetType = AuditTargetType.TREASURY_KEY,
      operatorId = "#command.operatorUserId()",
      targetId = "#result != null ? #result.walletAddress() : null")
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
      kmsKeyLifecyclePort.scheduleKeyDeletion(saved.getKmsKeyId(), DEFAULT_KMS_PENDING_WINDOW_DAYS);
      treasuryAuditRecorder.record(command.operatorUserId(), walletAddress, true, null);
      return TreasuryWalletView.from(saved);
    } catch (RuntimeException e) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }
}
