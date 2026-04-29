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
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletDisabledEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions a wallet ACTIVE → DISABLED and persists the row. KMS {@code DisableKey} is no
 * longer invoked inside this method — instead a {@link TreasuryWalletDisabledEvent} is published
 * and an AFTER_COMMIT handler invokes {@code DisableKmsKeyUseCase} so the KMS call only runs once
 * the DB transaction has committed.
 *
 * <p><b>Why split the transaction.</b> When the DB save and the KMS call sat in the same
 * {@code @Transactional}, a "KMS success → commit failure" race could leave KMS DISABLED while
 * the DB silently rolled back to ACTIVE — invisible to anyone reading the DB. Splitting them so
 * the DB commits first means the new failure mode is "DB DISABLED, KMS still ACTIVE", which is
 * recorded as a row in {@code web3_treasury_kms_audits} for operator follow-up. The signing path
 * already gates on the DB row's {@code status}, so the wallet is no longer usable the moment the
 * DB commit lands; the KMS-side disable is defence-in-depth that operators can retry idempotently.
 *
 * <p>Audit writes for the business-flow attempt itself still go through {@link
 * TreasuryAuditRecorder} ({@code REQUIRES_NEW}) so they survive an outer rollback.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisableTreasuryWalletService implements DisableTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
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
      applicationEventPublisher.publishEvent(
          new TreasuryWalletDisabledEvent(
              saved.getWalletAlias(),
              saved.getKmsKeyId(),
              walletAddress,
              command.operatorUserId()));
      treasuryAuditRecorder.record(command.operatorUserId(), walletAddress, true, null);
      return TreasuryWalletView.from(saved);
    } catch (RuntimeException e) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAddress, false, e.getClass().getSimpleName());
      throw e;
    }
  }
}
