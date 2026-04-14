package momzzangseven.mztkbe.modules.account.application.port.in;

import java.util.Optional;

/**
 * Input port for retrieving the authentication provider name of a user account. Used by other
 * modules that need provider information without depending on account internals.
 */
public interface GetAuthProviderUseCase {

  /**
   * Returns the authentication provider name (e.g. "LOCAL", "KAKAO", "GOOGLE") for the given user.
   *
   * @param userId the user ID
   * @return the provider name, or empty if no account exists
   */
  Optional<String> getProviderName(Long userId);
}
