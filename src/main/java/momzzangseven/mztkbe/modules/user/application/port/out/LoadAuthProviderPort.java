package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Optional;

/**
 * Output port for loading the authentication provider name from the account module. Implemented by
 * an infrastructure adapter that delegates to account module's {@code GetAuthProviderUseCase}.
 */
public interface LoadAuthProviderPort {

  /**
   * Returns the authentication provider name (e.g. "LOCAL", "KAKAO", "GOOGLE") for the given user.
   *
   * @param userId the user ID
   * @return the provider name, or empty if no account exists
   */
  Optional<String> loadProviderName(Long userId);
}
