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
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletArchivedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions DISABLED → ARCHIVED and persists the row. KMS {@code ScheduleKeyDeletion} is no
 * longer invoked inside this method — instead a {@link TreasuryWalletArchivedEvent} is published
 * and an AFTER_COMMIT handler invokes {@code ScheduleKmsKeyDeletionUseCase} so the KMS call only
 * runs once the DB transaction has committed.
 *
 * <p><b>Why split the transaction.</b> Same rationale as {@link DisableTreasuryWalletService}: a
 * single {@code @Transactional} would expose a "KMS scheduled-for-deletion → DB commit failed" race
 * that left KMS irreversibly mutated while the DB silently rolled back to {@code DISABLED}. With
 * the DB committing first, the residual failure mode is "DB ARCHIVED, KMS not scheduled" which is
 * recorded in {@code web3_treasury_kms_audits} for operator follow-up; re-archiving the same row is
 * idempotent for the DB and a no-op for KMS once the key is already pending deletion.
 *
 * <p>The default 30-day pending window matches the KMS minimum and gives operators a recovery
 * buffer before key material is destroyed.
 *
 * <p>Failure audit writes still happen inline via {@link TreasuryAuditRecorder} ({@code
 * REQUIRES_NEW}) so a caught exception leaves a record even when the outer transaction rolls back.
 * The success audit is moved to an AFTER_COMMIT handler ({@code TreasuryAuditEventHandler}) so it
 * only lands once the wallet state transition has actually committed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArchiveTreasuryWalletService implements ArchiveTreasuryWalletUseCase {

  /** Pending window passed to {@code KmsKeyLifecyclePort.scheduleKeyDeletion} on archive. */
  static final int DEFAULT_KMS_PENDING_WINDOW_DAYS = 30;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final ApplicationEventPublisher applicationEventPublisher;
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
      publishTreasuryWalletArchivedEvent(command, saved, walletAddress);
      return TreasuryWalletView.from(saved);
    } catch (RuntimeException e) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }

  private void publishTreasuryWalletArchivedEvent(
      ArchiveTreasuryWalletCommand command, TreasuryWallet saved, String walletAddress) {
    applicationEventPublisher.publishEvent(
        new TreasuryWalletArchivedEvent(
            saved.getWalletAlias(),
            saved.getKmsKeyId(),
            walletAddress,
            command.operatorUserId(),
            DEFAULT_KMS_PENDING_WINDOW_DAYS));
  }
}
