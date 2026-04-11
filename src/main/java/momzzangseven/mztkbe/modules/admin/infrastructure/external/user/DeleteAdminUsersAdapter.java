package momzzangseven.mztkbe.modules.admin.infrastructure.external.user;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.out.DeleteAdminUsersPort;
import momzzangseven.mztkbe.modules.user.application.port.in.HardDeleteUsersUseCase;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that hard-deletes user records associated with admin accounts. Delegates to
 * the user module's {@link HardDeleteUsersUseCase}.
 */
@Component
@RequiredArgsConstructor
public class DeleteAdminUsersAdapter implements DeleteAdminUsersPort {

  private final HardDeleteUsersUseCase hardDeleteUsersUseCase;

  @Override
  public void deleteUsers(List<Long> userIds) {
    hardDeleteUsersUseCase.hardDeleteUsers(userIds);
  }
}
