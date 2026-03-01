package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalExternalDisconnectProperties;
import momzzangseven.mztkbe.modules.user.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;
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
@DisplayName("ExternalDisconnectService unit test")
class ExternalDisconnectServiceTest {

  @Mock private ExternalDisconnectExecutor executor;
  @Mock private ExternalDisconnectTaskPort externalDisconnectTaskPort;

  private WithdrawalExternalDisconnectProperties props;
  private ExternalDisconnectService service;

  @BeforeEach
  void setUp() {
    props = new WithdrawalExternalDisconnectProperties();
    props.setInitialBackoff(2_500L);
    service = new ExternalDisconnectService(executor, props, externalDisconnectTaskPort);
  }

  @Test
  @DisplayName("disconnectOnWithdrawal does nothing for LOCAL users")
  void disconnectOnWithdrawal_withLocalProvider_skipsAll() {
    User localUser = baseUser(AuthProvider.LOCAL);

    service.disconnectOnWithdrawal(localUser);

    verify(executor, never()).disconnect(any(), any(), any());
    verify(externalDisconnectTaskPort, never()).save(any(ExternalDisconnectTask.class));
  }

  @Test
  @DisplayName("disconnectOnWithdrawal calls executor for supported social provider")
  void disconnectOnWithdrawal_withKakao_callsExecutor() {
    User kakaoUser = baseUser(AuthProvider.KAKAO);

    service.disconnectOnWithdrawal(kakaoUser);

    verify(executor).disconnect(AuthProvider.KAKAO, "provider-user", "encrypted-refresh");
    verify(externalDisconnectTaskPort, never()).save(any(ExternalDisconnectTask.class));
  }

  @Test
  @DisplayName("disconnectOnWithdrawal enqueues retry task when executor fails")
  void disconnectOnWithdrawal_whenExecutorFails_enqueuesRetryTask() {
    User googleUser = baseUser(AuthProvider.GOOGLE);
    when(externalDisconnectTaskPort.save(any(ExternalDisconnectTask.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.doThrow(new IllegalStateException("oauth error"))
        .when(executor)
        .disconnect(AuthProvider.GOOGLE, "provider-user", "encrypted-refresh");

    LocalDateTime before = LocalDateTime.now();
    service.disconnectOnWithdrawal(googleUser);

    ArgumentCaptor<ExternalDisconnectTask> taskCaptor =
        ArgumentCaptor.forClass(ExternalDisconnectTask.class);
    verify(externalDisconnectTaskPort).save(taskCaptor.capture());

    ExternalDisconnectTask task = taskCaptor.getValue();
    assertThat(task.getUserId()).isEqualTo(10L);
    assertThat(task.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(task.getProviderUserId()).isEqualTo("provider-user");
    assertThat(task.getEncryptedToken()).isEqualTo("encrypted-refresh");
    assertThat(task.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
    assertThat(task.getAttemptCount()).isEqualTo(1);
    assertThat(task.getLastError()).isEqualTo("IllegalStateException: oauth error");

    long delayMillis = ChronoUnit.MILLIS.between(before, task.getNextAttemptAt());
    assertThat(delayMillis).isBetween(2_000L, 4_000L);
  }

  private User baseUser(AuthProvider provider) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 11, 0);
    return User.builder()
        .id(10L)
        .email("user@example.com")
        .nickname("tester")
        .authProvider(provider)
        .providerUserId("provider-user")
        .googleRefreshToken("encrypted-refresh")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(now.minusDays(10))
        .updatedAt(now)
        .build();
  }
}
