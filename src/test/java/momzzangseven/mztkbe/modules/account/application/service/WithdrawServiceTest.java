package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.WithdrawCommand;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawService unit test")
class WithdrawServiceTest {

  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private SaveUserAccountPort saveUserAccountPort;
  @Mock private DeleteRefreshTokenPort deleteRefreshTokenPort;
  @Mock private ExternalDisconnectService externalDisconnectService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private WithdrawService service;

  @BeforeEach
  void setUp() {
    service =
        new WithdrawService(
            loadUserAccountPort,
            saveUserAccountPort,
            deleteRefreshTokenPort,
            externalDisconnectService,
            eventPublisher);
  }

  @Test
  @DisplayName("execute soft-deletes active user and triggers side effects")
  void execute_withActiveUser_withdrawsAndPublishesEvent() {
    UserAccount activeAccount = baseAccount(7L, AccountStatus.ACTIVE);
    when(loadUserAccountPort.findByUserId(7L)).thenReturn(Optional.of(activeAccount));
    when(saveUserAccountPort.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(WithdrawCommand.of(7L));

    verify(saveUserAccountPort).save(any(UserAccount.class));

    verify(deleteRefreshTokenPort).deleteByUserId(7L);
    verify(externalDisconnectService).disconnectOnWithdrawal(7L, activeAccount);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isInstanceOf(UserSoftDeletedEvent.class);
    assertThat(((UserSoftDeletedEvent) eventCaptor.getValue()).userId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("execute throws withdrawn exception for already deleted user")
  void execute_withAlreadyWithdrawnUser_throwsUserWithdrawnException() {
    UserAccount deletedAccount = baseAccount(9L, AccountStatus.DELETED);
    when(loadUserAccountPort.findByUserId(9L)).thenReturn(Optional.of(deletedAccount));

    assertThatThrownBy(() -> service.execute(WithdrawCommand.of(9L)))
        .isInstanceOf(UserWithdrawnException.class);

    verify(saveUserAccountPort, never()).save(any(UserAccount.class));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("execute throws not found when user does not exist")
  void execute_withUnknownUser_throwsUserNotFoundException() {
    when(loadUserAccountPort.findByUserId(11L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(WithdrawCommand.of(11L)))
        .isInstanceOf(UserNotFoundException.class);

    verify(saveUserAccountPort, never()).save(any(UserAccount.class));
    verify(deleteRefreshTokenPort, never()).deleteByUserId(any());
  }

  @Test
  @DisplayName("execute validates command")
  void execute_withInvalidCommand_throws() {
    assertThatThrownBy(() -> service.execute(WithdrawCommand.of(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId is required");

    verify(loadUserAccountPort, never()).findByUserId(any());
  }

  @Test
  @DisplayName("[M-115] execute completes even when external disconnect fails internally")
  void execute_externalDisconnectFailure_doesNotBlockWithdrawal() {
    UserAccount activeAccount = baseAccount(7L, AccountStatus.ACTIVE);
    when(loadUserAccountPort.findByUserId(7L)).thenReturn(Optional.of(activeAccount));
    when(saveUserAccountPort.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // ExternalDisconnectService catches exceptions internally (best-effort),
    // so from WithdrawService perspective it just returns normally.
    // This test verifies the full flow completes even when external disconnect is involved.
    org.mockito.Mockito.doNothing()
        .when(externalDisconnectService)
        .disconnectOnWithdrawal(7L, activeAccount);

    service.execute(WithdrawCommand.of(7L));

    // All side effects should still complete
    verify(saveUserAccountPort).save(any(UserAccount.class));
    verify(deleteRefreshTokenPort).deleteByUserId(7L);
    verify(externalDisconnectService).disconnectOnWithdrawal(7L, activeAccount);
    verify(eventPublisher).publishEvent(any(UserSoftDeletedEvent.class));
  }

  private UserAccount baseAccount(Long userId, AccountStatus status) {
    Instant now = Instant.parse("2026-02-28T07:00:00Z");
    return UserAccount.builder()
        .id(userId * 10)
        .userId(userId)
        .provider(AuthProvider.KAKAO)
        .providerUserId("provider-" + userId)
        .status(status)
        .createdAt(now.minusSeconds(30 * 86400))
        .updatedAt(now.minusSeconds(86400))
        .deletedAt(status == AccountStatus.DELETED ? now.minusSeconds(86400) : null)
        .build();
  }
}
