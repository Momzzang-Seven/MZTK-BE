package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.WithdrawCommand;
import momzzangseven.mztkbe.modules.account.application.port.in.WithdrawUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

  private final LoadUserAccountPort loadUserAccountPort;
  private final SaveUserAccountPort saveUserAccountPort;
  private final DeleteRefreshTokenPort deleteRefreshTokenPort;
  private final ExternalDisconnectService externalDisconnectService;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void execute(WithdrawCommand command) {
    command.validate();

    UserAccount account =
        loadUserAccountPort
            .findByUserId(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

    if (account.isDeleted()) {
      log.info("Withdraw request for already withdrawn user: userId={}", command.userId());
      throw new UserWithdrawnException();
    }

    saveUserAccountPort.save(account.withdraw());

    deleteRefreshTokenPort.deleteByUserId(command.userId());

    // External provider disconnection is best-effort. Failures must not rollback withdrawal.
    externalDisconnectService.disconnectOnWithdrawal(command.userId(), account);

    // Publish user soft-deleted event, trigger registered wallet to be USER_DELETED status.
    eventPublisher.publishEvent(new UserSoftDeletedEvent(command.userId()));

    log.info(
        "User withdrawal completed: userId={}, provider={}",
        command.userId(),
        account.getProvider());
  }
}
