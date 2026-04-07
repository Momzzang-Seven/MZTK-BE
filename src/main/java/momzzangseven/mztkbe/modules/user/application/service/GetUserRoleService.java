package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserRoleUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service implementing {@link GetUserRoleUseCase}. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetUserRoleService implements GetUserRoleUseCase {

  private final LoadUserPort loadUserPort;

  @Override
  public UserRole getUserRole(Long userId) {
    return loadUserPort
        .loadUserById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId))
        .getRole();
  }
}
