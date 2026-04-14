package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Hexagonal Architecture: This is an OUTPUT PORT that defines operations needed by the application
 * layer. Implemented by an adapter in the infrastructure layer, allowing the application layer to
 * remain independent of infrastructure details.
 */
public interface LoadUserPort {

  Optional<User> loadUserByEmail(String email);

  Optional<User> loadUserById(Long userId);

  boolean existsByEmail(String email);

  List<User> loadUsersByIds(Collection<Long> userIds);
}
