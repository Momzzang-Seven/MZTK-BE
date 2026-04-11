package momzzangseven.mztkbe.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.AuthenticateAdminLocalUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates an admin account using local credentials (loginId + password). Verifies the
 * credentials against the stored BCrypt hash and updates the last-login timestamp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticateAdminLocalService implements AuthenticateAdminLocalUseCase {

  private final LoadAdminAccountPort loadAdminAccountPort;
  private final AdminPasswordEncoderPort adminPasswordEncoderPort;
  private final SaveAdminAccountPort saveAdminAccountPort;

  @Override
  @Transactional
  public AuthenticateAdminLocalResult execute(AuthenticateAdminLocalCommand command) {
    AdminAccount account =
        loadAdminAccountPort
            .findActiveByLoginId(command.loginId())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid admin credentials"));

    if (!adminPasswordEncoderPort.matches(command.password(), account.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid admin credentials");
    }

    try {
      saveAdminAccountPort.saveAndFlush(account.updateLastLogin());
    } catch (ObjectOptimisticLockingFailureException e) {
      log.warn(
          "Admin account deleted during login (concurrent recovery reseed): {}", command.loginId());
      throw new InvalidCredentialsException("Admin account no longer exists");
    }

    return new AuthenticateAdminLocalResult(account.getUserId());
  }
}
