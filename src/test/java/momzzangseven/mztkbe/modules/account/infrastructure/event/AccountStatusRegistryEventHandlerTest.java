package momzzangseven.mztkbe.modules.account.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.account.application.dto.ApplyAccountStatusChangeCommand;
import momzzangseven.mztkbe.modules.account.application.port.in.ApplyAccountStatusChangeUseCase;
import momzzangseven.mztkbe.modules.account.domain.event.UserAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.account.domain.event.UserAccountStatusChangedEvent;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStatusRegistryEventHandler unit test")
class AccountStatusRegistryEventHandlerTest {

  @Mock private ApplyAccountStatusChangeUseCase applyAccountStatusChangeUseCase;

  @InjectMocks private AccountStatusRegistryEventHandler accountStatusRegistryEventHandler;

  @Test
  @DisplayName("onStatusChanged(BLOCKED) forwards a put command carrying the BLOCKED status")
  void onStatusChanged_blocked_forwardsCommand() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(100L, AccountStatus.BLOCKED);

    accountStatusRegistryEventHandler.onStatusChanged(event);

    verify(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(100L, AccountStatus.BLOCKED));
  }

  @Test
  @DisplayName("onStatusChanged(ACTIVE) forwards the ACTIVE status; the service decides evict")
  void onStatusChanged_active_forwardsCommand() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(200L, AccountStatus.ACTIVE);

    accountStatusRegistryEventHandler.onStatusChanged(event);

    verify(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(200L, AccountStatus.ACTIVE));
  }

  @Test
  @DisplayName("onInvalidated forwards a null-status command (evict) for the hard-deleted user")
  void onInvalidated_forwardsNullStatusCommand() {
    UserAccountInvalidatedEvent event = new UserAccountInvalidatedEvent(300L);

    accountStatusRegistryEventHandler.onInvalidated(event);

    verify(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(300L, null));
  }

  @Test
  @DisplayName("onStatusChanged swallows the UseCase exception because update is post-commit")
  void onStatusChanged_swallowsException() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(400L, AccountStatus.BLOCKED);
    doThrow(new RuntimeException("denylist fail"))
        .when(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(400L, AccountStatus.BLOCKED));

    assertThatCode(() -> accountStatusRegistryEventHandler.onStatusChanged(event))
        .doesNotThrowAnyException();
    verify(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(400L, AccountStatus.BLOCKED));
  }

  @Test
  @DisplayName("onInvalidated swallows the UseCase exception because eviction is post-commit")
  void onInvalidated_swallowsException() {
    UserAccountInvalidatedEvent event = new UserAccountInvalidatedEvent(500L);
    doThrow(new RuntimeException("denylist fail"))
        .when(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(500L, null));

    assertThatCode(() -> accountStatusRegistryEventHandler.onInvalidated(event))
        .doesNotThrowAnyException();
    verify(applyAccountStatusChangeUseCase)
        .execute(new ApplyAccountStatusChangeCommand(500L, null));
  }
}
