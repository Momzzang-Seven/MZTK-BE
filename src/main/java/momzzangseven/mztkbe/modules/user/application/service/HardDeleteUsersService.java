package momzzangseven.mztkbe.modules.user.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.application.port.in.HardDeleteUsersUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.DeleteUserPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link HardDeleteUsersUseCase}. Called by the account module's hard-delete
 * batch process.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class HardDeleteUsersService implements HardDeleteUsersUseCase {

  private final DeleteUserPort deleteUserPort;

  @Override
  public void hardDeleteUsers(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    log.info("Hard-deleting {} user(s): {}", userIds.size(), userIds);
    deleteUserPort.deleteAllByIdInBatch(userIds);
  }
}
