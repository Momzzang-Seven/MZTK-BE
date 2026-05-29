package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuaranteedGrantXpService unit test")
class GuaranteedGrantXpServiceTest {

  @Mock private GrantXpUseCase grantXpUseCase;
  @Mock private XpGrantOutboxPort outboxPort;

  private GuaranteedGrantXpService service;

  @BeforeEach
  void setUp() {
    service = new GuaranteedGrantXpService(grantXpUseCase, outboxPort);
  }

  private GrantXpCommand command() {
    return GrantXpCommand.of(
        1L, XpType.POST, LocalDateTime.now(), "post:create:1", "post-creation:1");
  }

  @Test
  @DisplayName("synchronous grant succeeds -> returns the result, never touches the outbox")
  void grantSucceeds_noOutbox() {
    GrantXpCommand cmd = command();
    GrantXpResult granted = GrantXpResult.granted(10, 3, 1, LocalDate.now());
    when(grantXpUseCase.execute(cmd)).thenReturn(granted);

    GrantXpResult result = service.execute(cmd);

    assertThat(result).isEqualTo(granted);
    verify(outboxPort, never()).enqueue(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("synchronous grant throws -> enqueues to outbox and returns DEFERRED")
  void grantFails_enqueuesAndReturnsDeferred() {
    GrantXpCommand cmd = command();
    when(grantXpUseCase.execute(cmd)).thenThrow(new IllegalStateException("xp system down"));

    GrantXpResult result = service.execute(cmd);

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.DEFERRED);
    assertThat(result.grantedXp()).isZero();
    verify(outboxPort).enqueue(cmd);
  }

  @Test
  @DisplayName("grant throws and outbox enqueue also throws -> still DEFERRED, never propagates")
  void grantAndEnqueueBothFail_returnsDeferredWithoutThrowing() {
    GrantXpCommand cmd = command();
    when(grantXpUseCase.execute(cmd)).thenThrow(new IllegalStateException("xp system down"));
    doThrow(new RuntimeException("outbox down")).when(outboxPort).enqueue(cmd);

    assertThatCode(
            () -> {
              GrantXpResult result = service.execute(cmd);
              assertThat(result.status()).isEqualTo(GrantXpResult.Status.DEFERRED);
              assertThat(result.grantedXp()).isZero();
            })
        .doesNotThrowAnyException();

    verify(outboxPort).enqueue(cmd);
  }
}
