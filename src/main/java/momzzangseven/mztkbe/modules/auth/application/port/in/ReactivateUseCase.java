package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.dto.ReactivateCommand;

/** Input port for reactivating a soft-deleted account and issuing tokens. */
public interface ReactivateUseCase {
  LoginResult execute(ReactivateCommand command);
}
