package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ArchiveTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.RecordTreasuryAuditUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasArchivedAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions a whole cohort DISABLED → ARCHIVED in one transaction (MOM-444). A cohort is the set
 * of wallet rows sharing one {@code (treasury_address, kms_key_id)} pair; invariant #2 requires
 * every row in a cohort to share the same status, so archive is cohort-wide rather than single-row.
 *
 * <p><b>Flow.</b> Load the trigger row by alias, acquire the address advisory lock, load the full
 * cohort {@code FOR UPDATE}, strict-reject if the cohort is not uniformly DISABLED, transition
 * every row, and {@code saveAll}. Then publish one {@link AliasArchivedAuditEvent} per alias (audit
 * is alias-level) and exactly one {@link KeyLifecycleEvent.ScheduledDeletion} (KMS {@code
 * ScheduleKeyDeletion} is key-level — once per cohort).
 *
 * <p><b>Why split the transaction.</b> Same rationale as {@link DisableTreasuryWalletService}: a
 * single {@code @Transactional} would expose a "KMS scheduled-for-deletion → DB commit failed"
 * race. With the DB committing first, the residual failure mode is "DB ARCHIVED, KMS not
 * scheduled", recorded in {@code web3_treasury_kms_audits} for operator follow-up.
 *
 * <p>The default 30-day pending window matches the KMS minimum and gives operators a recovery
 * buffer before key material is destroyed.
 *
 * <p>Failure audits happen inline via {@link RecordTreasuryAuditUseCase} ({@code REQUIRES_NEW});
 * the success audit is the AFTER_COMMIT {@link AliasArchivedAuditEvent} handler.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArchiveTreasuryWalletService implements ArchiveTreasuryWalletUseCase {

  /** Pending window passed to {@code KmsKeyLifecyclePort.scheduleKeyDeletion} on archive. */
  static final int DEFAULT_KMS_PENDING_WINDOW_DAYS = 30;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final RecordTreasuryAuditUseCase treasuryAuditRecorder;
  private final TreasuryAdvisoryLockPort treasuryAdvisoryLockPort;
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
    TreasuryWallet trigger =
        loadTreasuryWalletPort
            .loadByAlias(command.walletAlias())
            .orElseThrow(
                () ->
                    new TreasuryWalletStateException(
                        "Treasury wallet '" + command.walletAlias() + "' not found"));

    String walletAddress = trigger.getWalletAddress();
    treasuryAdvisoryLockPort.lockForAddress(walletAddress);

    List<TreasuryWallet> cohort =
        loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(walletAddress);
    assertCohortUniformlyDisabled(command, cohort, walletAddress);

    try {
      List<TreasuryWallet> archivedCohort =
          cohort.stream().map(wallet -> wallet.archive(clock)).toList();
      List<TreasuryWallet> saved = saveTreasuryWalletPort.saveAll(archivedCohort);

      saved.forEach(
          wallet ->
              applicationEventPublisher.publishEvent(
                  new AliasArchivedAuditEvent(
                      wallet.getWalletAlias(), walletAddress, command.operatorUserId())));
      applicationEventPublisher.publishEvent(
          new KeyLifecycleEvent.ScheduledDeletion(
              trigger.getKmsKeyId(),
              trigger.getWalletAlias(),
              walletAddress,
              command.operatorUserId(),
              DEFAULT_KMS_PENDING_WINDOW_DAYS));

      return saved.stream()
          .filter(wallet -> wallet.getWalletAlias().equals(command.walletAlias()))
          .findFirst()
          .map(TreasuryWalletView::from)
          .orElseThrow(
              () ->
                  new TreasuryWalletStateException(
                      "archived cohort no longer contains trigger alias '"
                          + command.walletAlias()
                          + "'"));
    } catch (RuntimeException e) {
      treasuryAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          walletAddress,
          false,
          e.getClass().getSimpleName());
      throw e;
    }
  }

  /**
   * Strict-reject a cohort that is not uniformly DISABLED — invariant #2. Records a {@code
   * COHORT_STATE_INCONSISTENT} failure audit before throwing; the exception message carries the
   * status breakdown for diagnosis.
   */
  private void assertCohortUniformlyDisabled(
      ArchiveTreasuryWalletCommand command, List<TreasuryWallet> cohort, String walletAddress) {
    boolean uniformlyDisabled =
        !cohort.isEmpty()
            && cohort.stream().allMatch(w -> w.getStatus() == TreasuryWalletStatus.DISABLED);
    if (!uniformlyDisabled) {
      treasuryAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          walletAddress,
          false,
          "COHORT_STATE_INCONSISTENT");
      throw new TreasuryWalletStateException(
          "Treasury cohort for address '"
              + walletAddress
              + "' cannot be archived — not uniformly DISABLED: "
              + statusBreakdown(cohort));
    }
  }

  private static String statusBreakdown(List<TreasuryWallet> cohort) {
    Map<TreasuryWalletStatus, Long> counts =
        cohort.stream()
            .collect(Collectors.groupingBy(TreasuryWallet::getStatus, Collectors.counting()));
    return counts.toString();
  }
}
