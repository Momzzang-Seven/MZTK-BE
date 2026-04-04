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
import momzzangseven.mztkbe.global.error.user.IllegalTrainerGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateUserRoleService unit test")
class UpdateUserRoleServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;

  private UpdateUserRoleService service;

  @BeforeEach
  void setUp() {
    service = new UpdateUserRoleService(loadUserPort, saveUserPort);
  }

  @Test
  @DisplayName("execute validates command before loading user")
  void execute_withInvalidCommand_throws() {
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(null, UserRole.USER);

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class);

    verify(loadUserPort, never()).loadUserById(any());
  }

  @Test
  @DisplayName("execute throws when target user does not exist")
  void execute_withUnknownUser_throwsUserNotFound() {
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(1L, UserRole.TRAINER);
    when(loadUserPort.loadUserById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(UserNotFoundException.class);

    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  @Test
  @DisplayName("execute blocks trainer grant when business rule fails")
  void execute_withIneligibleTrainerGrant_throws() {
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(1L, UserRole.TRAINER);
    User user = baseUser(1L, null, UserRole.USER);
    when(loadUserPort.loadUserById(1L)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(IllegalTrainerGrantException.class)
        .hasMessageContaining("User does not meet requirements to become a trainer");

    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  @Test
  @DisplayName("execute rejects role update when target role is unchanged")
  void execute_withSameRole_throwsInvalidUserRole() {
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(1L, UserRole.USER);
    User user = baseUser(1L, "user@example.com", UserRole.USER);
    when(loadUserPort.loadUserById(1L)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(InvalidUserRoleException.class)
        .hasMessageContaining("same as current");

    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  @Test
  @DisplayName("execute updates role and returns validated result")
  void execute_withValidInput_updatesAndReturnsResult() {
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(1L, UserRole.TRAINER);
    User user = baseUser(1L, "user@example.com", UserRole.USER);
    when(loadUserPort.loadUserById(1L)).thenReturn(Optional.of(user));
    when(saveUserPort.saveUser(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UpdateUserRoleResult result = service.execute(command);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).saveUser(userCaptor.capture());
    User savedUser = userCaptor.getValue();

    assertThat(savedUser.getRole()).isEqualTo(UserRole.TRAINER);
    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.role()).isEqualTo(UserRole.TRAINER);
  }

  private User baseUser(Long id, String email, UserRole role) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 12, 0);
    return User.builder()
        .id(id)
        .email(email)
        .nickname("tester")
        .authProvider(AuthProvider.LOCAL)
        .role(role)
        .status(UserStatus.ACTIVE)
        .createdAt(now.minusDays(5))
        .updatedAt(now.minusDays(1))
        .build();
  }
}
