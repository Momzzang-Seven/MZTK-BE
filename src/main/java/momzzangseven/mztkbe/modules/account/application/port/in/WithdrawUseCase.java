package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.WithdrawCommand;

/** Use case for withdrawing (soft-deleting) a user account. */
public interface WithdrawUseCase {

  void execute(WithdrawCommand command);
}
