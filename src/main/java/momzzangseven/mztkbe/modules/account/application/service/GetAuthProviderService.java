package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.in.GetAuthProviderUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service that retrieves the authentication provider for a user account. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAuthProviderService implements GetAuthProviderUseCase {

  private final LoadUserAccountPort loadUserAccountPort;

  @Override
  public Optional<String> getProviderName(Long userId) {
    return loadUserAccountPort
        .findByUserId(userId)
        .map(UserAccount::getProvider)
        .map(AuthProvider::name);
  }
}
