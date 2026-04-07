package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.account.application.dto.SignupResult;

/** Input port for signup use case. */
public interface SignupUseCase {
  SignupResult execute(SignupCommand command);
}
