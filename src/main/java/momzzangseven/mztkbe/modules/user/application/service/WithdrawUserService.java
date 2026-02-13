package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.auth.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.user.application.dto.WithdrawUserCommand;
import momzzangseven.mztkbe.modules.user.application.port.in.WithdrawUserUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WithdrawUserService implements WithdrawUserUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;
  private final DeleteRefreshTokenPort deleteRefreshTokenPort;
  private final ExternalDisconnectService externalDisconnectService;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void execute(WithdrawUserCommand command) {
    command.validate();

    User user = loadActiveUser(command.userId());

    User withdrawnUser = user.withdraw();
    saveUserPort.saveUser(withdrawnUser);

    deleteRefreshTokenPort.deleteByUserId(user.getId());

    // External provider disconnection is best-effort. Failures must not rollback withdrawal.
    externalDisconnectService.disconnectOnWithdrawal(withdrawnUser);

    // Publish user soft-deleted event, trigger registered wallet to be USER_DELETED status.
    eventPublisher.publishEvent(new UserSoftDeletedEvent(user.getId()));

    log.info(
        "User withdrawal completed: userId={}, provider={}", user.getId(), user.getAuthProvider());
  }

  private User loadActiveUser(Long userId) {
    return loadUserPort
        .loadUserById(userId)
        .orElseGet(
            () -> {
              if (loadUserPort.loadDeletedUserById(userId).isPresent()) {
                log.info("Withdraw request for already withdrawn user: userId={}", userId);
                throw new UserWithdrawnException();
              }
              throw new UserNotFoundException(userId);
            });
  }
}
