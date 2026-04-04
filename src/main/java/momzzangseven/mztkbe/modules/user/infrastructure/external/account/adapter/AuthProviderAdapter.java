package momzzangseven.mztkbe.modules.user.infrastructure.external.account.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.in.GetAuthProviderUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadAuthProviderPort;
import org.springframework.stereotype.Component;

/**
 * Driven adapter that bridges the user module's {@link LoadAuthProviderPort} to the account
 * module's {@link GetAuthProviderUseCase}. Follows the same cross-module delegation pattern used by
 * account's infrastructure adapters (e.g. UserInfoAdapter → LoadUserInfoUseCase).
 */
@Component
@RequiredArgsConstructor
public class AuthProviderAdapter implements LoadAuthProviderPort {

  private final GetAuthProviderUseCase getAuthProviderUseCase;

  @Override
  public Optional<String> loadProviderName(Long userId) {
    return getAuthProviderUseCase.getProviderName(userId);
  }
}
