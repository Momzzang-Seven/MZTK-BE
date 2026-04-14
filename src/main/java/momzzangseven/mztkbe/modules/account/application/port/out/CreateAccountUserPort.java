package momzzangseven.mztkbe.modules.account.application.port.out;

import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;

/**
 * Output port for creating a user profile. Implemented by an infrastructure adapter that delegates
 * to the user module's {@code CreateUserUseCase}.
 */
public interface CreateAccountUserPort {

  AccountUserSnapshot createUser(
      String email, String nickname, String profileImageUrl, String role);
}
