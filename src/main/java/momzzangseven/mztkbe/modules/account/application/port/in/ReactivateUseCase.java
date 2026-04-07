package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.account.application.dto.ReactivateCommand;

/** Input port for reactivating a soft-deleted account and issuing tokens. */
public interface ReactivateUseCase {
  LoginResult execute(ReactivateCommand command);
}
