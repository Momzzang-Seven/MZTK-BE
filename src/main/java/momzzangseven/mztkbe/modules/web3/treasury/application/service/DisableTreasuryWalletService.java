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
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasDisabledAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions a whole cohort ACTIVE → DISABLED in one transaction (MOM-444). A cohort is the set of
 * wallet rows sharing one {@code (treasury_address, kms_key_id)} pair; invariant #2 requires every
 * row in a cohort to share the same status, so disable is cohort-wide rather than single-row.
 *
 * <p><b>Flow.</b> Load the trigger row by alias, acquire the address advisory lock, load the full
 * cohort {@code FOR UPDATE}, strict-reject if the cohort is not uniformly ACTIVE, transition every
 * row, and {@code saveAll}. Then publish one {@link AliasDisabledAuditEvent} per alias (audit is
 * alias-level) and exactly one {@link KeyLifecycleEvent.Disabled} (KMS {@code DisableKey} is
 * key-level — once per cohort).
 *
 * <p><b>Why split the transaction.</b> When the DB save and the KMS call sat in the same
 * {@code @Transactional}, a "KMS success → commit failure" race could leave KMS DISABLED while the
 * DB silently rolled back to ACTIVE. Splitting them so the DB commits first means the residual
 * failure mode is "DB DISABLED, KMS still ACTIVE", recorded in {@code web3_treasury_kms_audits} for
 * operator follow-up.
 *
 * <p>Failure audits happen inline via {@link TreasuryAuditRecorder} ({@code REQUIRES_NEW}) so a
 * caught exception leaves a record even when the outer transaction rolls back. The success audit is
 * the AFTER_COMMIT {@link AliasDisabledAuditEvent} handler.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisableTreasuryWalletService implements DisableTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final TreasuryAdvisoryLockPort treasuryAdvisoryLockPort;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "TREASURY_KEY_DISABLE",
      targetType = AuditTargetType.TREASURY_KEY,
      operatorId = "#command.operatorUserId()",
      targetId = "#result != null ? #result.walletAddress() : null")
  public TreasuryWalletView execute(DisableTreasuryWalletCommand command) {
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
    assertCohortUniformlyActive(command, cohort, walletAddress);

    try {
      List<TreasuryWallet> disabledCohort =
          cohort.stream().map(wallet -> wallet.disable(clock)).toList();
      List<TreasuryWallet> saved = saveTreasuryWalletPort.saveAll(disabledCohort);

      saved.forEach(
          wallet ->
              applicationEventPublisher.publishEvent(
                  new AliasDisabledAuditEvent(
                      wallet.getWalletAlias(), walletAddress, command.operatorUserId())));
      applicationEventPublisher.publishEvent(
          new KeyLifecycleEvent.Disabled(
              trigger.getKmsKeyId(),
              trigger.getWalletAlias(),
              walletAddress,
              command.operatorUserId()));

      return saved.stream()
          .filter(wallet -> wallet.getWalletAlias().equals(command.walletAlias()))
          .findFirst()
          .map(TreasuryWalletView::from)
          .orElseThrow(
              () ->
                  new TreasuryWalletStateException(
                      "disabled cohort no longer contains trigger alias '"
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
   * Strict-reject a cohort that is not uniformly ACTIVE — invariant #2. Records a {@code
   * COHORT_STATE_INCONSISTENT} failure audit before throwing so operators can see the rejected
   * attempt; the exception message carries the status breakdown for diagnosis.
   */
  private void assertCohortUniformlyActive(
      DisableTreasuryWalletCommand command, List<TreasuryWallet> cohort, String walletAddress) {
    boolean uniformlyActive =
        !cohort.isEmpty()
            && cohort.stream().allMatch(w -> w.getStatus() == TreasuryWalletStatus.ACTIVE);
    if (!uniformlyActive) {
      treasuryAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          walletAddress,
          false,
          "COHORT_STATE_INCONSISTENT");
      throw new TreasuryWalletStateException(
          "Treasury cohort for address '"
              + walletAddress
              + "' cannot be disabled — not uniformly ACTIVE: "
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
