package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service that checks account status via the account persistence layer. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CheckAccountStatusService implements CheckAccountStatusUseCase {

  private final LoadUserAccountPort loadUserAccountPort;

  @Override
  public boolean isActive(Long userId) {
    return loadUserAccountPort.findByUserId(userId).map(UserAccount::isActive).orElse(false);
  }

  @Override
  public boolean isDeleted(Long userId) {
    return loadUserAccountPort.findByUserId(userId).map(UserAccount::isDeleted).orElse(false);
  }

  @Override
  public boolean isBlocked(Long userId) {
    return loadUserAccountPort.findByUserId(userId).map(UserAccount::isBlocked).orElse(false);
  }
}
