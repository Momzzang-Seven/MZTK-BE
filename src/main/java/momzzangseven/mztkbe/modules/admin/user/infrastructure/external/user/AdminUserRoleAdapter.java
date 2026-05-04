package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.user;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserRolePort;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserRole;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserRoleUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserRoleAdapter implements LoadAdminUserRolePort {

  private final GetUserRoleUseCase getUserRoleUseCase;

  @Override
  public AdminUserRole load(Long userId) {
    return toAdminUserRole(getUserRoleUseCase.getUserRole(userId));
  }

  private AdminUserRole toAdminUserRole(UserRole role) {
    return AdminUserRole.valueOf(role.name());
  }
}
