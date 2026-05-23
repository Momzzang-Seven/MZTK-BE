package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ReplayTerminatedExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class TransactionalReplayTerminatedExecutionIntentUseCaseTest {

  @Test
  @DisplayName("terminated replay는 intent lock/repair transaction과 evidence/hook replay를 분리한다")
  void executeRunsIntentResolutionInRequiresNewOnly() {
    ReplayTerminatedExecutionIntentService delegate =
        mock(ReplayTerminatedExecutionIntentService.class);
    ExecutionIntent intent = mock(ExecutionIntent.class);
    ReplayTerminatedExecutionIntentCommand command =
        new ReplayTerminatedExecutionIntentCommand("intent-1", "MARKETPLACE_ADMIN_REFUND");
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    TransactionalReplayTerminatedExecutionIntentUseCase useCase =
        new TransactionalReplayTerminatedExecutionIntentUseCase(delegate, transactionManager);

    when(delegate.resolveReplayTarget(command)).thenReturn(intent);
    when(delegate.replayTerminated(intent)).thenReturn(true);

    assertThat(useCase.execute(command)).isTrue();

    assertThat(transactionManager.begunRequiresNew).isEqualTo(1);
    assertThat(transactionManager.commits).isEqualTo(1);
    InOrder inOrder = inOrder(delegate);
    inOrder.verify(delegate).resolveReplayTarget(command);
    inOrder.verify(delegate).replayTerminated(intent);
  }

  private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    int begunRequiresNew;
    int commits;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
        begunRequiresNew++;
      }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
