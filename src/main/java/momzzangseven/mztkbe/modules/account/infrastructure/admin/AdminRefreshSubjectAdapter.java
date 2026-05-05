package momzzangseven.mztkbe.modules.account.infrastructure.admin;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.out.CheckAdminRefreshSubjectPort;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import org.springframework.stereotype.Component;

/** Bridges account token reissue checks to the admin account status use case. */
@Component
@RequiredArgsConstructor
public class AdminRefreshSubjectAdapter implements CheckAdminRefreshSubjectPort {

  private final CheckAdminAccountStatusUseCase checkAdminAccountStatusUseCase;

  @Override
  public boolean isActiveAdmin(Long userId) {
    return checkAdminAccountStatusUseCase.isActiveAdmin(userId);
  }
}
