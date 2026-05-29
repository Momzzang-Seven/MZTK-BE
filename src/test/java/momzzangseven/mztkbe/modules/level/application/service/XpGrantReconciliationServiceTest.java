package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationResult;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("XpGrantReconciliationService unit test")
class XpGrantReconciliationServiceTest {

  @Mock private XpGrantOutboxPort outboxPort;
  @Mock private XpGrantOutboxProcessor processor;

  private XpGrantReconciliationService service;

  @BeforeEach
  void setUp() {
    service = new XpGrantReconciliationService(outboxPort, processor);
  }

  private PendingXpGrant pending(long id) {
    return new PendingXpGrant(
        id,
        GrantXpCommand.of(
            id, XpType.POST, LocalDateTime.of(2026, 5, 29, 9, 0), "post:create:" + id, "ref:" + id),
        0);
  }

  @Test
  @DisplayName("granted and skipped rows are counted; no failure recorded")
  void run_countsGrantedAndSkipped() {
    when(outboxPort.findDueBatch(any(LocalDateTime.class), anyInt()))
        .thenReturn(List.of(pending(1L), pending(2L)));
    when(processor.process(1L)).thenReturn(true);
    when(processor.process(2L)).thenReturn(false);

    RunXpGrantReconciliationResult result =
        service.run(new RunXpGrantReconciliationCommand(10, 5, 30));

    assertThat(result.scanned()).isEqualTo(2);
    assertThat(result.granted()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    verify(outboxPort, never()).recordFailure(any(), anyInt(), anyInt(), any());
  }

  @Test
  @DisplayName(
      "a throwing process is isolated: failed++ and recordFailure invoked with command args")
  void run_recordsFailureWhenProcessThrows() {
    when(outboxPort.findDueBatch(any(LocalDateTime.class), anyInt()))
        .thenReturn(List.of(pending(3L)));
    when(processor.process(3L)).thenThrow(new RuntimeException("boom"));

    RunXpGrantReconciliationResult result =
        service.run(new RunXpGrantReconciliationCommand(10, 5, 30));

    assertThat(result.scanned()).isEqualTo(1);
    assertThat(result.granted()).isZero();
    assertThat(result.skipped()).isZero();
    assertThat(result.failed()).isEqualTo(1);
    verify(outboxPort).recordFailure(3L, 5, 30, "boom");
  }
}
