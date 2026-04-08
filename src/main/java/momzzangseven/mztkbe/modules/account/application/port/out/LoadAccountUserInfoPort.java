package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;

/**
 * Output port for retrieving user profile data. Implemented by an infrastructure adapter that
 * delegates to the user module's {@code LoadUserInfoUseCase}.
 */
public interface LoadAccountUserInfoPort {

  Optional<AccountUserSnapshot> findById(Long userId);

  Optional<AccountUserSnapshot> findByEmail(String email);

  boolean existsByEmail(String email);
}
