package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.user;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserRolePort;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserRoleUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserRoleAdapter implements LoadAdminUserRolePort {

  private final GetUserRoleUseCase getUserRoleUseCase;

  @Override
  public UserRole load(Long userId) {
    return getUserRoleUseCase.getUserRole(userId);
  }
}
