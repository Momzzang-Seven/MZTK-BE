package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions a wallet ACTIVE → DISABLED, persists, then asks KMS to disable the backing key.
 *
 * <p>Audit writes go through {@link TreasuryAuditRecorder} (separate bean) so they run in {@code
 * REQUIRES_NEW} and survive a rollback of the outer transaction. An inline
 * {@code @Transactional(REQUIRES_NEW)} method on this same class would not work — Spring AOP cannot
 * intercept self-invocation, so the propagation hint would be silently dropped.
 *
 * <p><b>Save-first ordering</b> — both the DB save and the KMS {@code DisableKey} call run inside a
 * single {@link Transactional}; the JPA flush precedes the KMS call and the actual COMMIT happens
 * when the method returns. KMS failure rolls the transaction back, so DB and KMS stay consistent in
 * the common path. The narrow inconsistent window is <em>KMS success → DB commit failure</em>
 * (rare, e.g. connection drop after the KMS call): the row stays ACTIVE in the DB while the KMS key
 * is already disabled. Recovery is manual re-disable against the same row, which is idempotent on
 * both sides. We intentionally diverge from the design doc §4-4 KMS-first sequence because the
 * inverse failure mode (DB unchanged + KMS invisibly disabled with no DB pointer) is harder to
 * detect from operator-side dashboards.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisableTreasuryWalletService implements DisableTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final Clock clock;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "TREASURY_KEY_DISABLE",
      targetType = AuditTargetType.TREASURY_KEY,
      operatorId = "#command.operatorUserId()",
      targetId = "#result != null ? #result.walletAddress() : null")
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
      treasuryAuditRecorder.record(command.operatorUserId(), walletAddress, true, null);
      return TreasuryWalletView.from(saved);
    } catch (RuntimeException e) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }
}
