package momzzangseven.mztkbe.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.admin.AdminAccountNotFoundException;
import momzzangseven.mztkbe.global.error.admin.WeakAdminPasswordException;
import momzzangseven.mztkbe.modules.admin.application.dto.RotateAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.port.in.RotateAdminPasswordUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for rotating an admin's own password (self-rotation). */
@Service
@RequiredArgsConstructor
public class RotateAdminPasswordService implements RotateAdminPasswordUseCase {

  private static final int MIN_PASSWORD_LENGTH = 20;

  private final LoadAdminAccountPort loadAdminAccountPort;
  private final AdminPasswordEncoderPort adminPasswordEncoderPort;
  private final SaveAdminAccountPort saveAdminAccountPort;

  @Override
  @Transactional
  public void execute(RotateAdminPasswordCommand command) {
    command.validate();

    AdminAccount account =
        loadAdminAccountPort
            .findActiveByUserId(command.userId())
            .orElseThrow(AdminAccountNotFoundException::new);

    if (!adminPasswordEncoderPort.matches(command.currentPassword(), account.getPasswordHash())) {
      throw new InvalidCredentialsException("Current password does not match");
    }

    validatePasswordPolicy(command.newPassword());

    String newHash = adminPasswordEncoderPort.encode(command.newPassword());
    saveAdminAccountPort.save(account.rotatePassword(newHash));
  }

  private void validatePasswordPolicy(String password) {
    if (password.length() < MIN_PASSWORD_LENGTH) {
      throw new WeakAdminPasswordException(
          "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    if (!password.matches(".*[A-Z].*")) {
      throw new WeakAdminPasswordException("Password must contain at least one uppercase letter");
    }
    if (!password.matches(".*[a-z].*")) {
      throw new WeakAdminPasswordException("Password must contain at least one lowercase letter");
    }
    if (!password.matches(".*\\d.*")) {
      throw new WeakAdminPasswordException("Password must contain at least one digit");
    }
    if (!password.matches(".*[!@#$%^&*()\\-_=+].*")) {
      throw new WeakAdminPasswordException("Password must contain at least one special character");
    }
  }
}
