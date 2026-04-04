package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteUserLevelDataPort;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.HardDeleteUsersPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadHardDeletePolicyPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.event.UsersHardDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalHardDeleteService unit test")
class WithdrawalHardDeleteServiceTest {

  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private HardDeleteUsersPort hardDeleteUsersPort;
  @Mock private DeleteUserAccountPort deleteUserAccountPort;
  @Mock private ExternalDisconnectTaskPort externalDisconnectTaskPort;
  @Mock private DeleteRefreshTokenPort deleteRefreshTokenPort;
  @Mock private DeleteUserLevelDataPort deleteUserLevelDataPort;
  @Mock private LoadHardDeletePolicyPort policyPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private WithdrawalHardDeleteService service;

  @BeforeEach
  void setUp() {
    service =
        new WithdrawalHardDeleteService(
            loadUserAccountPort,
            hardDeleteUsersPort,
            deleteUserAccountPort,
            externalDisconnectTaskPort,
            deleteRefreshTokenPort,
            deleteUserLevelDataPort,
            policyPort,
            eventPublisher);
  }

  @Test
  @DisplayName("runBatch rejects non-positive retention days")
  void runBatch_withInvalidRetention_throws() {
    when(policyPort.getRetentionDays()).thenReturn(0);

    assertThatThrownBy(() -> service.runBatch(Instant.parse("2026-02-28T17:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("withdrawal.hard-delete.retention-days must be > 0");

    verify(loadUserAccountPort, never())
        .findUserIdsForHardDeletion(any(Instant.class), any(Integer.class));
  }

  @Test
  @DisplayName("runBatch rejects non-positive batch size")
  void runBatch_withInvalidBatchSize_throws() {
    when(policyPort.getRetentionDays()).thenReturn(30);
    when(policyPort.getBatchSize()).thenReturn(0);

    assertThatThrownBy(() -> service.runBatch(Instant.parse("2026-02-28T17:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("withdrawal.hard-delete.batch-size must be > 0");

    verify(loadUserAccountPort, never())
        .findUserIdsForHardDeletion(any(Instant.class), any(Integer.class));
  }

  @Test
  @DisplayName("runBatch rejects null current time")
  void runBatch_withNullNow_throws() {
    when(policyPort.getRetentionDays()).thenReturn(30);
    when(policyPort.getBatchSize()).thenReturn(100);

    assertThatThrownBy(() -> service.runBatch(null)).isInstanceOf(NullPointerException.class);

    verify(loadUserAccountPort, never())
        .findUserIdsForHardDeletion(any(Instant.class), any(Integer.class));
  }

  @Test
  @DisplayName("runBatch returns zero when there are no deletion targets")
  void runBatch_withNoTargets_returnsZero() {
    Instant now = Instant.parse("2026-02-28T17:00:00Z");
    when(policyPort.getRetentionDays()).thenReturn(30);
    when(policyPort.getBatchSize()).thenReturn(100);
    when(loadUserAccountPort.findUserIdsForHardDeletion(any(Instant.class), eq(100)))
        .thenReturn(List.of());

    int deleted = service.runBatch(now);

    assertThat(deleted).isZero();
    verify(deleteRefreshTokenPort, never()).deleteByUserIdIn(any());
    verify(hardDeleteUsersPort, never()).hardDeleteUsers(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("runBatch deletes related data and users when targets are found")
  void runBatch_withTargets_executesDeletionFlow() {
    Instant now = Instant.parse("2026-02-28T17:00:00Z");
    List<Long> userIds = List.of(3L, 5L, 8L);

    when(policyPort.getRetentionDays()).thenReturn(30);
    when(policyPort.getBatchSize()).thenReturn(100);
    when(loadUserAccountPort.findUserIdsForHardDeletion(any(Instant.class), eq(100)))
        .thenReturn(userIds);

    int deleted = service.runBatch(now);

    assertThat(deleted).isEqualTo(3);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isInstanceOf(UsersHardDeletedEvent.class);
    assertThat(((UsersHardDeletedEvent) eventCaptor.getValue()).userIds())
        .containsExactly(3L, 5L, 8L);

    verify(deleteUserLevelDataPort).deleteByUserIds(userIds);

    verify(deleteRefreshTokenPort).deleteByUserIdIn(userIds);
    verify(externalDisconnectTaskPort).deleteByUserIdIn(userIds);
    verify(deleteUserAccountPort).deleteByUserIdIn(userIds);
    verify(hardDeleteUsersPort).hardDeleteUsers(userIds);
  }
}
