package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DisableKmsKeyService implements DisableKmsKeyUseCase {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @Override
  public void execute(DisableKmsKeyCommand command) {
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
}
