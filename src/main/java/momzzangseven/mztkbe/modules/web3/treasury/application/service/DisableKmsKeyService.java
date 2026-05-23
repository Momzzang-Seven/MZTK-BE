package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MOM-444 Disable AFTER_COMMIT KMS orchestration. Calls {@code KmsKeyLifecyclePort.disableKey} and
 * records the outcome to {@code web3_treasury_kms_audits} as a {@code KMS_DISABLE} row.
 *
 * <p>Idempotent on stale events: re-reads the {@code treasury_wallets} row under {@code
 * PESSIMISTIC_WRITE} lock; if the row's {@code (kmsKeyId, status=DISABLED)} no longer matches the
 * command, records a {@code KMS_DISABLE_SKIPPED} audit ({@code success=true}, reason ∈ {@code
 * {ROW_MISSING, KEY_ID_MISMATCH, STATUS_MISMATCH}}) and returns without invoking AWS KMS. This
 * symmetric CAS gate blocks the reverse-order Disable/Reactivate race in which a delayed Disable
 * handler would otherwise drift KMS to DISABLED while the row already sits ACTIVE after C5
 * reEnableSameKey.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisableKmsKeyService implements DisableKmsKeyUseCase {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;
  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(DisableKmsKeyCommand command) {
    Optional<TreasuryWallet> currentOpt =
        loadTreasuryWalletPort.loadByAliasForUpdate(command.walletAlias());

    String staleReason = detectStaleReason(currentOpt, command);
    if (staleReason != null) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE_SKIPPED,
          true,
          staleReason);
      return;
    }

    try {
      kmsKeyLifecyclePort.disableKey(command.kmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          true,
          null);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private String detectStaleReason(
      Optional<TreasuryWallet> currentOpt, DisableKmsKeyCommand command) {
    if (currentOpt.isEmpty()) {
      return "ROW_MISSING";
    }
    TreasuryWallet current = currentOpt.get();
    if (!command.kmsKeyId().equals(current.getKmsKeyId())) {
      return "KEY_ID_MISMATCH";
    }
    if (current.getStatus() != TreasuryWalletStatus.DISABLED) {
      return "STATUS_MISMATCH";
    }
    return null;
  }
}
