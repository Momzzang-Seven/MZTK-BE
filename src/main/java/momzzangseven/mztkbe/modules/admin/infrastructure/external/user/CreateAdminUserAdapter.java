package momzzangseven.mztkbe.modules.admin.infrastructure.external.user;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.out.CreateAdminUserPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.CreateUserUseCase;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that creates a User record for admin accounts. Uses the user module's
 * SaveUserPort to persist the admin user.
 */
@Component
@RequiredArgsConstructor
public class CreateAdminUserAdapter implements CreateAdminUserPort {

  private final CreateUserUseCase createUserUseCase;

  @Override
  public Long createAdmin(String email, String nickname, AdminRole adminRole) {

    UserInfo userInfo =
        createUserUseCase.createAdminUser(
            new CreateUserCommand(email, nickname, null, adminRole.toString()));

    return userInfo.id();
  }
}
