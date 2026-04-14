package momzzangseven.mztkbe.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks whether an active admin account exists for a given user ID. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CheckAdminAccountStatusService implements CheckAdminAccountStatusUseCase {

  private final LoadAdminAccountPort loadAdminAccountPort;

  @Override
  public boolean isActiveAdmin(Long userId) {
    return loadAdminAccountPort.findActiveByUserId(userId).isPresent();
  }
}
