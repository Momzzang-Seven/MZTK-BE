package momzzangseven.mztkbe.modules.user.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;

/**
 * Inbound port for retrieving user profile information. Intended for use by other modules (e.g.
 * account) that need user data without depending on the User domain model directly.
 */
public interface LoadUserInfoUseCase {

  Optional<UserInfo> loadUserById(Long userId);

  Optional<UserInfo> loadUserByEmail(String email);

  boolean existsByEmail(String email);
}
