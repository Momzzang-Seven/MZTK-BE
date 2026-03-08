package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.auth.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.dto.WithdrawUserCommand;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawUserService unit test")
class WithdrawUserServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private DeleteRefreshTokenPort deleteRefreshTokenPort;
  @Mock private ExternalDisconnectService externalDisconnectService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private WithdrawUserService service;

  @BeforeEach
  void setUp() {
    service =
        new WithdrawUserService(
            loadUserPort,
            saveUserPort,
            deleteRefreshTokenPort,
            externalDisconnectService,
            eventPublisher);
  }

  @Test
  @DisplayName("execute soft-deletes active user and triggers side effects")
  void execute_withActiveUser_withdrawsAndPublishesEvent() {
    User activeUser = baseUser(7L, UserStatus.ACTIVE);
    when(loadUserPort.loadUserById(7L)).thenReturn(Optional.of(activeUser));
    when(saveUserPort.saveUser(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(WithdrawUserCommand.of(7L));

    ArgumentCaptor<User> withdrawnCaptor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).saveUser(withdrawnCaptor.capture());
    User withdrawn = withdrawnCaptor.getValue();
    assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(withdrawn.getDeletedAt()).isNotNull();

    verify(deleteRefreshTokenPort).deleteByUserId(7L);
    verify(externalDisconnectService).disconnectOnWithdrawal(withdrawn);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isInstanceOf(UserSoftDeletedEvent.class);
    assertThat(((UserSoftDeletedEvent) eventCaptor.getValue()).userId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("execute throws withdrawn exception for already deleted user")
  void execute_withAlreadyWithdrawnUser_throwsUserWithdrawnException() {
    when(loadUserPort.loadUserById(9L)).thenReturn(Optional.empty());
    when(loadUserPort.loadDeletedUserById(9L))
        .thenReturn(Optional.of(baseUser(9L, UserStatus.DELETED)));

    assertThatThrownBy(() -> service.execute(WithdrawUserCommand.of(9L)))
        .isInstanceOf(UserWithdrawnException.class);

    verify(saveUserPort, never()).saveUser(any(User.class));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("execute throws not found when user does not exist")
  void execute_withUnknownUser_throwsUserNotFoundException() {
    when(loadUserPort.loadUserById(11L)).thenReturn(Optional.empty());
    when(loadUserPort.loadDeletedUserById(11L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(WithdrawUserCommand.of(11L)))
        .isInstanceOf(UserNotFoundException.class);

    verify(saveUserPort, never()).saveUser(any(User.class));
    verify(deleteRefreshTokenPort, never()).deleteByUserId(any());
  }

  @Test
  @DisplayName("execute validates command")
  void execute_withInvalidCommand_throws() {
    assertThatThrownBy(() -> service.execute(WithdrawUserCommand.of(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId is required");

    verify(loadUserPort, never()).loadUserById(any());
  }

  private User baseUser(Long id, UserStatus status) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 16, 0);
    return User.builder()
        .id(id)
        .email("user" + id + "@example.com")
        .nickname("user" + id)
        .authProvider(AuthProvider.KAKAO)
        .providerUserId("provider-" + id)
        .role(UserRole.USER)
        .status(status)
        .createdAt(now.minusDays(30))
        .updatedAt(now.minusDays(1))
        .deletedAt(status == UserStatus.DELETED ? now.minusDays(1) : null)
        .build();
  }
}
