package momzzangseven.mztkbe.modules.admin.infrastructure.external.user;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.out.CreateAdminUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that creates a User record for admin accounts. Uses the user module's
 * SaveUserPort to persist the admin user.
 */
@Component
@RequiredArgsConstructor
public class CreateAdminUserAdapter implements CreateAdminUserPort {

  private final SaveUserPort saveUserPort;

  @Override
  public Long createAdmin(String email, String nickname, UserRole adminRole) {
    User adminUser = User.createAdmin(email, nickname, adminRole);
    User saved = saveUserPort.saveUser(adminUser);
    return saved.getId();
  }
}
