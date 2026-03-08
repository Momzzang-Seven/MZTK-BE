package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.auth.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.DeleteUserLevelDataUseCase;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalHardDeleteProperties;
import momzzangseven.mztkbe.modules.user.application.port.out.DeleteUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.event.UsersHardDeletedEvent;
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
@DisplayName("WithdrawalHardDeleteService unit test")
class WithdrawalHardDeleteServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private DeleteUserPort deleteUserPort;
  @Mock private ExternalDisconnectTaskPort externalDisconnectTaskPort;
  @Mock private DeleteRefreshTokenPort deleteRefreshTokenPort;
  @Mock private DeleteUserLevelDataUseCase deleteUserLevelDataUseCase;
  @Mock private ApplicationEventPublisher eventPublisher;

  private WithdrawalHardDeleteProperties props;
  private WithdrawalHardDeleteService service;

  @BeforeEach
  void setUp() {
    props = new WithdrawalHardDeleteProperties();
    props.setRetentionDays(30);
    props.setBatchSize(100);
    service =
        new WithdrawalHardDeleteService(
            loadUserPort,
            deleteUserPort,
            externalDisconnectTaskPort,
            deleteRefreshTokenPort,
            deleteUserLevelDataUseCase,
            props,
            eventPublisher);
  }

  @Test
  @DisplayName("runBatch rejects non-positive retention days")
  void runBatch_withInvalidRetention_throws() {
    props.setRetentionDays(0);

    assertThatThrownBy(() -> service.runBatch(LocalDateTime.of(2026, 2, 28, 17, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("withdrawal.hard-delete.retention-days must be > 0");

    verify(loadUserPort, never()).loadUserIdsForDeletion(any(), any(), any(Integer.class));
  }

  @Test
  @DisplayName("runBatch rejects non-positive batch size")
  void runBatch_withInvalidBatchSize_throws() {
    props.setBatchSize(0);

    assertThatThrownBy(() -> service.runBatch(LocalDateTime.of(2026, 2, 28, 17, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("withdrawal.hard-delete.batch-size must be > 0");

    verify(loadUserPort, never()).loadUserIdsForDeletion(any(), any(), any(Integer.class));
  }

  @Test
  @DisplayName("runBatch rejects null current time")
  void runBatch_withNullNow_throws() {
    assertThatThrownBy(() -> service.runBatch(null)).isInstanceOf(NullPointerException.class);

    verify(loadUserPort, never()).loadUserIdsForDeletion(any(), any(), any(Integer.class));
  }

  @Test
  @DisplayName("runBatch returns zero when there are no deletion targets")
  void runBatch_withNoTargets_returnsZero() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 17, 0);
    when(loadUserPort.loadUserIdsForDeletion(
            eq(UserStatus.DELETED), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of());

    int deleted = service.runBatch(now);

    assertThat(deleted).isZero();
    verify(deleteRefreshTokenPort, never()).deleteByUserIdIn(any());
    verify(deleteUserPort, never()).deleteAllByIdInBatch(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("runBatch deletes related data and users when targets are found")
  void runBatch_withTargets_executesDeletionFlow() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 17, 0);
    List<Long> userIds = List.of(3L, 5L, 8L);

    when(loadUserPort.loadUserIdsForDeletion(
            eq(UserStatus.DELETED), any(LocalDateTime.class), eq(100)))
        .thenReturn(userIds);

    int deleted = service.runBatch(now);

    assertThat(deleted).isEqualTo(3);

    ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(loadUserPort)
        .loadUserIdsForDeletion(eq(UserStatus.DELETED), cutoffCaptor.capture(), eq(100));
    assertThat(cutoffCaptor.getValue()).isEqualTo(now.minusDays(30));

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isInstanceOf(UsersHardDeletedEvent.class);
    assertThat(((UsersHardDeletedEvent) eventCaptor.getValue()).userIds())
        .containsExactly(3L, 5L, 8L);

    ArgumentCaptor<DeleteUserLevelDataCommand> levelCommandCaptor =
        ArgumentCaptor.forClass(DeleteUserLevelDataCommand.class);
    verify(deleteUserLevelDataUseCase).execute(levelCommandCaptor.capture());
    assertThat(levelCommandCaptor.getValue().userIds()).containsExactly(3L, 5L, 8L);

    verify(deleteRefreshTokenPort).deleteByUserIdIn(userIds);
    verify(externalDisconnectTaskPort).deleteByUserIdIn(userIds);
    verify(deleteUserPort).deleteAllByIdInBatch(userIds);
  }
}
