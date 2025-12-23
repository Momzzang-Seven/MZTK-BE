package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Hexagonal Architecture: This is an OUTPUT PORT that defines operations needed by the application
 * layer. Implemented by an adapter in the infrastructure layer, allowing the application layer to
 * remain independent of infrastructure details.
 */
public interface LoadUserPort {

  Optional<User> loadUserByEmail(String email);

  boolean existsByEmail(String email);

  Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
