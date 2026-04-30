package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.BindKmsAliasCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.BindKmsAliasUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.stereotype.Service;

/**
 * Idempotent {@code CreateAlias} / {@code UpdateAlias} executor. Hosted as a use-case so the
 * AFTER_COMMIT handler can stay a thin adapter over Spring event wiring while the alias-binding
 * policy (ghost-alias recovery vs. out-of-band conflict) lives in the application layer.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BindKmsAliasService implements BindKmsAliasUseCase {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @Override
  public void execute(BindKmsAliasCommand command) {
    try {
      kmsKeyLifecyclePort.createAlias(command.walletAlias(), command.kmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_CREATE_ALIAS,
          true,
          null);
    } catch (KmsAliasAlreadyExistsException ex) {
      handleAliasAlreadyExists(command, ex);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_CREATE_ALIAS,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private void handleAliasAlreadyExists(BindKmsAliasCommand command, RuntimeException original) {
    KmsKeyState existingTarget = kmsKeyLifecyclePort.describeAliasTarget(command.walletAlias());
    if (existingTarget == KmsKeyState.PENDING_DELETION
        || existingTarget == KmsKeyState.DISABLED
        || existingTarget == KmsKeyState.UNAVAILABLE) {
      log.warn(
          "Recovering ghost alias from a prior failed provision run "
              + "(alias={}, previousState={}); rebinding to kmsKeyId={}",
          command.walletAlias(),
          existingTarget,
          command.kmsKeyId());
      try {
        kmsKeyLifecyclePort.updateAlias(command.walletAlias(), command.kmsKeyId());
        kmsAuditRecorder.record(
            command.operatorUserId(),
            command.walletAlias(),
            command.kmsKeyId(),
            command.walletAddress(),
            KmsAuditAction.KMS_UPDATE_ALIAS,
            true,
            null);
      } catch (RuntimeException ex) {
        kmsAuditRecorder.record(
            command.operatorUserId(),
            command.walletAlias(),
            command.kmsKeyId(),
            command.walletAddress(),
            KmsAuditAction.KMS_UPDATE_ALIAS,
            false,
            ex.getClass().getSimpleName());
        throw ex;
      }
    } else {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_CREATE_ALIAS,
          false,
          original.getClass().getSimpleName());
      throw new TreasuryWalletAlreadyProvisionedException(
          "alias '"
              + command.walletAlias()
              + "' is already bound to a KMS key in state "
              + existingTarget
              + " (out-of-band provisioning detected)");
    }
  }
}
