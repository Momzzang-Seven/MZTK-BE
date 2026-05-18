package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.EnableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MOM-444 ReEnableSameKey (C5) AFTER_COMMIT KMS orchestration. Calls {@code
 * KmsKeyLifecyclePort.enableKey} and records the outcome to {@code web3_treasury_kms_audits} as a
 * {@code KMS_ENABLE} row.
 *
 * <p>Idempotent on stale events: re-reads the {@code treasury_wallets} row under {@code
 * PESSIMISTIC_WRITE} lock; if the row's {@code (kmsKeyId, status=ACTIVE)} no longer matches the
 * command, records a {@code KMS_ENABLE_SKIPPED} audit ({@code success=true}, reason ∈ {@code
 * {ROW_MISSING, KEY_ID_MISMATCH, STATUS_MISMATCH}}) and returns without invoking AWS KMS.
 */
@Service
@RequiredArgsConstructor
public class EnableKmsKeyService implements EnableKmsKeyUseCase {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;
  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(EnableKmsKeyCommand command) {
    Optional<TreasuryWallet> currentOpt =
        loadTreasuryWalletPort.loadByAliasForUpdate(command.walletAlias());

    String staleReason = detectStaleReason(currentOpt, command);
    if (staleReason != null) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_ENABLE_SKIPPED,
          true,
          staleReason);
      return;
    }

    try {
      kmsKeyLifecyclePort.enableKey(command.kmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_ENABLE,
          true,
          null);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_ENABLE,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private String detectStaleReason(
      Optional<TreasuryWallet> currentOpt, EnableKmsKeyCommand command) {
    if (currentOpt.isEmpty()) {
      return "ROW_MISSING";
    }
    TreasuryWallet current = currentOpt.get();
    if (!command.kmsKeyId().equals(current.getKmsKeyId())) {
      return "KEY_ID_MISMATCH";
    }
    if (current.getStatus() != TreasuryWalletStatus.ACTIVE) {
      return "STATUS_MISMATCH";
    }
    return null;
  }
}
