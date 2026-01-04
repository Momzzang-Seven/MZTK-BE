package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.WithdrawUserCommand;

/** Use case for withdrawing (soft-deleting) the current user. */
public interface WithdrawUserUseCase {
  void execute(WithdrawUserCommand command);
}
