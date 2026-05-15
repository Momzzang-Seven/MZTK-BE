package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SpringRunAfterCommitAdapterTest {

  @Mock private PlatformTransactionManager transactionManager;

  private SpringRunAfterCommitAdapter adapter;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    adapter = new SpringRunAfterCommitAdapter(transactionManager);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void runAfterCommit_registersActionAndRunsItInRequiresNewTransactionAfterCommit() {
    AtomicInteger calls = new AtomicInteger();
    TransactionSynchronizationManager.initSynchronization();

    adapter.runAfterCommit(calls::incrementAndGet);

    assertThat(calls).hasValue(0);
    TransactionSynchronization synchronization =
        TransactionSynchronizationManager.getSynchronizations().getFirst();
    synchronization.afterCommit();

    assertThat(calls).hasValue(1);
    ArgumentCaptor<TransactionDefinition> definitionCaptor =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definitionCaptor.capture());
    assertThat(definitionCaptor.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    verify(transactionManager).commit(any());
  }

  @Test
  void runAfterCommit_swallowsActionFailureAfterRollingBackNewTransaction() {
    assertThatCode(
            () ->
                adapter.runAfterCommit(
                    () -> {
                      throw new IllegalStateException("failed");
                    }))
        .doesNotThrowAnyException();

    verify(transactionManager).rollback(any());
  }
}
