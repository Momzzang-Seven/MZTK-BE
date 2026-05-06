package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.application.dto.UpdateManagedUserAccountStatusCommand;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateManagedUserAccountStatusService 단위 테스트")
class UpdateManagedUserAccountStatusServiceTest {

  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private SaveUserAccountPort saveUserAccountPort;

  @InjectMocks private UpdateManagedUserAccountStatusService service;

  @Test
  @DisplayName("ACTIVE 계정을 BLOCKED 로 변경한다")
  void execute_activeToBlocked() {
    UserAccount active = account(AccountStatus.ACTIVE);
    given(loadUserAccountPort.findByUserIdForUpdate(21L)).willReturn(Optional.of(active));
    given(saveUserAccountPort.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.execute(new UpdateManagedUserAccountStatusCommand(21L, AccountStatus.BLOCKED));

    ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
    verify(saveUserAccountPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(AccountStatus.BLOCKED);
    assertThat(result.userId()).isEqualTo(21L);
    assertThat(result.status()).isEqualTo(AccountStatus.BLOCKED);
  }

  @Test
  @DisplayName("BLOCKED 계정을 ACTIVE 로 변경한다")
  void execute_blockedToActive() {
    UserAccount blocked = account(AccountStatus.BLOCKED);
    given(loadUserAccountPort.findByUserIdForUpdate(21L)).willReturn(Optional.of(blocked));
    given(saveUserAccountPort.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.execute(new UpdateManagedUserAccountStatusCommand(21L, AccountStatus.ACTIVE));

    assertThat(result.userId()).isEqualTo(21L);
    assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("이미 같은 status 이면 idempotent 성공하고 저장하지 않는다")
  void execute_sameStatus_isIdempotent() {
    UserAccount blocked = account(AccountStatus.BLOCKED);
    given(loadUserAccountPort.findByUserIdForUpdate(21L)).willReturn(Optional.of(blocked));

    var result =
        service.execute(new UpdateManagedUserAccountStatusCommand(21L, AccountStatus.BLOCKED));

    assertThat(result.userId()).isEqualTo(21L);
    assertThat(result.status()).isEqualTo(AccountStatus.BLOCKED);
    verify(saveUserAccountPort, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("DELETED 계정은 관리자 정지/해제 대상으로 변경하지 않는다")
  void execute_deletedAccount_throws() {
    UserAccount deleted = account(AccountStatus.DELETED);
    given(loadUserAccountPort.findByUserIdForUpdate(21L)).willReturn(Optional.of(deleted));

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateManagedUserAccountStatusCommand(21L, AccountStatus.ACTIVE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only ACTIVE or BLOCKED");

    verify(saveUserAccountPort, never()).save(org.mockito.ArgumentMatchers.any());
  }

  private UserAccount account(AccountStatus status) {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return UserAccount.builder()
        .id(1L)
        .userId(21L)
        .provider(AuthProvider.LOCAL)
        .providerUserId(null)
        .passwordHash("hash")
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
