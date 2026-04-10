package momzzangseven.mztkbe.modules.admin.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.admin.AdminAccountNotFoundException;
import momzzangseven.mztkbe.global.error.admin.SelfResetForbiddenException;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.ResetPeerAdminPasswordUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.GenerateCredentialPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for resetting another admin's password (peer-reset). */
@Service
@RequiredArgsConstructor
public class ResetPeerAdminPasswordService implements ResetPeerAdminPasswordUseCase {

  private final LoadAdminAccountPort loadAdminAccountPort;
  private final GenerateCredentialPort generateCredentialPort;
  private final AdminPasswordEncoderPort adminPasswordEncoderPort;
  private final SaveAdminAccountPort saveAdminAccountPort;

  @Override
  @Transactional
  public ResetPeerAdminPasswordResult execute(ResetPeerAdminPasswordCommand command) {
    if (command.operatorUserId().equals(command.targetUserId())) {
      throw new SelfResetForbiddenException();
    }

    AdminAccount target =
        loadAdminAccountPort
            .findActiveByUserId(command.targetUserId())
            .orElseThrow(AdminAccountNotFoundException::new);

    String newPlaintext = generateCredentialPort.generatePasswordOnly();
    String newHash = adminPasswordEncoderPort.encode(newPlaintext);

    AdminAccount rotated = target.rotatePassword(newHash);
    saveAdminAccountPort.save(rotated);

    return new ResetPeerAdminPasswordResult(
        target.getUserId(), target.getLoginId(), newPlaintext, Instant.now());
  }
}
