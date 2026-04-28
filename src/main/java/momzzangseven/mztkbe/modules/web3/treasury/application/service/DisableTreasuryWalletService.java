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
치 * <p>Audit writes go through {@link TreasuryAuditRecorder} (separate bean) so they run in {@code
 * REQUIRES_NEW} and survive a rollback of the outer transaction. An inline
 * {@code @Transactional(REQUIRES_NEW)} method on this same class would not work — Spring AOP cannot
 * intercept self-invocation, so the propagation hint would be silently dropped.
 *
 * <p><b>Save-first ordering</b> — DB save commits before the KMS {@code DisableKey} call. If KMS
 * fails the row is already DISABLED and an operator can retry the KMS step or trigger manual
 * cleanup; a KMS-first ordering would leave the DB unchanged and the KMS key invisibly disabled,
 * which is harder to recover from.
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
