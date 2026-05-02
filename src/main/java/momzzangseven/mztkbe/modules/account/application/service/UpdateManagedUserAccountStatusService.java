package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusCommand;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusResult;
import momzzangseven.mztkbe.modules.account.application.port.in.UpdateManagedUserAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account-module use case for admin-managed user suspension and restoration. */
@Service
@RequiredArgsConstructor
public class UpdateManagedUserAccountStatusService
    implements UpdateManagedUserAccountStatusUseCase {

  private final LoadUserAccountPort loadUserAccountPort;
  private final SaveUserAccountPort saveUserAccountPort;

  @Override
  @Transactional
  public UpdateManagedUserAccountStatusResult execute(
      UpdateManagedUserAccountStatusCommand command) {
    command.validate();

    UserAccount account =
        loadUserAccountPort
            .findByUserId(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));
    UserAccount changed = account.changeManagedStatus(command.status());
    UserAccount persisted = changed == account ? account : saveUserAccountPort.save(changed);

    return new UpdateManagedUserAccountStatusResult(persisted.getUserId(), persisted.getStatus());
  }
}
