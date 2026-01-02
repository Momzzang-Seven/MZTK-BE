package momzzangseven.mztkbe.modules.user.application.port.out;

import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Outbound Port for persisting user data.
 *
 * <p>Hexagonal Architecture: - This is an OUTPUT PORT that defines operations needed by the
 * application layer - Implemented by an ADAPTER in the infrastructure layer - Allows the
 * application layer to remain independent of infrastructure details
 */
public interface SaveUserPort {

  /**
   * Save (create or update) a user.
   *
   * <p>Business Rules: - If user.id is null, creates a new user - If user.id is not null, updates
   * existing user - Automatically sets createdAt on new users - Automatically updates updatedAt on
   * all saves
   *
   * @param user the user to save
   * @return the saved user with generated ID (if new)
   */
  User saveUser(User user);
}
