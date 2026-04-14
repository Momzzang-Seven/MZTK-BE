package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Inbound port for retrieving a user's role. */
public interface GetUserRoleUseCase {

  UserRole getUserRole(Long userId);
}
