package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.AliasTargetInfo;
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
    AliasTargetInfo existing = kmsKeyLifecyclePort.describeAlias(command.walletAlias());
    KmsKeyState state = existing.state();
    String targetKmsKeyId = existing.targetKmsKeyId();
    boolean aliasMatchesIntent = command.kmsKeyId().equals(targetKmsKeyId);

    // (1) Idempotent success: alias is ENABLED and already points at the intended kmsKeyId. A
    // concurrent or replayed bind reached AWS first — record success and stop without mutating
    // the alias.
    if (state == KmsKeyState.ENABLED && aliasMatchesIntent) {
      log.info(
          "Alias already bound to the intended kmsKeyId (alias={}, kmsKeyId={}) — treating"
              + " AlreadyExists as idempotent success",
          command.walletAlias(),
          command.kmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_CREATE_ALIAS,
          true,
          null);
      return;
    }

    // (2) Recoverable: classic ghost-alias from a prior failed run ({DISABLED, PENDING_DELETION,
    // UNAVAILABLE}) — or PR #177 R3 alias drift (ENABLED but pointing at a different key than the
    // wallet row records). In every case the DB row is the source of truth, so we rebind via
    // updateAlias.
    boolean ghostState =
        state == KmsKeyState.PENDING_DELETION
            || state == KmsKeyState.DISABLED
            || state == KmsKeyState.UNAVAILABLE;
    boolean enabledDrift = state == KmsKeyState.ENABLED && !aliasMatchesIntent;
    if (ghostState || enabledDrift) {
      log.warn(
          "Recovering alias from inconsistent AWS state (alias={}, previousState={},"
              + " previousTarget={}, repairReason={}); rebinding to kmsKeyId={}",
          command.walletAlias(),
          state,
          targetKmsKeyId,
          enabledDrift ? "ENABLED_DRIFT" : "GHOST",
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
      return;
    }

    // (3) Fall-through: states we don't auto-recover from (e.g. PENDING_IMPORT). Treat as
    // out-of-band conflict and surface to the operator.
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
            + state
            + " (targetKmsKeyId="
            + targetKmsKeyId
            + ", expected="
            + command.kmsKeyId()
            + "); out-of-band provisioning detected");
  }
}
