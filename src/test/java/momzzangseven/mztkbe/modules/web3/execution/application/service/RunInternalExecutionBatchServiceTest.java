package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunInternalExecutionBatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");

  @Mock private ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase;
  @Mock private LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort;

  private RunInternalExecutionBatchService service;

  @BeforeEach
  void setUp() {
    service =
        new RunInternalExecutionBatchService(
            executeInternalExecutionIntentUseCase, loadInternalExecutionIssuerPolicyPort);
    when(loadInternalExecutionIssuerPolicyPort.loadPolicy())
        .thenReturn(
            new LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy(
                true, 5, List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));
  }

  @Test
  void runBatch_usesConfiguredActionTypesAndCountsStatuses() {
    when(executeInternalExecutionIntentUseCase.execute(any()))
        .thenReturn(
            new ExecuteInternalExecutionIntentResult(
                true,
                false,
                "intent-1",
                ExecutionIntentStatus.PENDING_ONCHAIN,
                1L,
                ExecutionTransactionStatus.PENDING,
                "0x1"),
            new ExecuteInternalExecutionIntentResult(
                true,
                false,
                "intent-2",
                ExecutionIntentStatus.SIGNED,
                2L,
                ExecutionTransactionStatus.SIGNED,
                "0x2"),
            ExecuteInternalExecutionIntentResult.notFound());

    var result = service.runBatch(NOW);

    assertThat(result.executedCount()).isEqualTo(2);
    assertThat(result.pendingCount()).isEqualTo(1);
    assertThat(result.signedCount()).isEqualTo(1);
    assertThat(result.quarantinedCount()).isZero();
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void runBatch_countsQuarantinedResultAndContinues() {
    when(executeInternalExecutionIntentUseCase.execute(any()))
        .thenReturn(
            ExecuteInternalExecutionIntentResult.quarantined(
                "intent-quarantined", ExecutionIntentStatus.CANCELED),
            ExecuteInternalExecutionIntentResult.notFound());

    var result = service.runBatch(NOW);

    assertThat(result.executedCount()).isEqualTo(1);
    assertThat(result.pendingCount()).isZero();
    assertThat(result.signedCount()).isZero();
    assertThat(result.quarantinedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void runBatch_marksFailedAndStopsWhenExecutionThrows() {
    when(executeInternalExecutionIntentUseCase.execute(any()))
        .thenThrow(new IllegalStateException("boom"));

    var result = service.runBatch(NOW);

    assertThat(result.executedCount()).isZero();
    assertThat(result.pendingCount()).isZero();
    assertThat(result.signedCount()).isZero();
    assertThat(result.quarantinedCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
  }
}
