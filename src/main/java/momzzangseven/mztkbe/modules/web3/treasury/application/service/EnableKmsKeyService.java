package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.EnableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.stereotype.Service;

/**
 * MOM-444 ReEnableSameKey (C5) AFTER_COMMIT KMS orchestration. Calls {@code
 * KmsKeyLifecyclePort.enableKey} and records the outcome to {@code web3_treasury_kms_audits} as a
 * {@code KMS_ENABLE} row.
 */
@Service
@RequiredArgsConstructor
public class EnableKmsKeyService implements EnableKmsKeyUseCase {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @Override
  public void execute(EnableKmsKeyCommand command) {
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
}
