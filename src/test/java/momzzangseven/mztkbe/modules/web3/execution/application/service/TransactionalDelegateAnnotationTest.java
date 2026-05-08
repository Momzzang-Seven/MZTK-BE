package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.error.web3.ExecutionIntentTerminalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Structural test asserting that {@link TransactionalExecuteExecutionIntentDelegate} preserves
 * MOM-397 transaction-boundary intent: terminal exceptions must NOT roll back the intent state
 * transition (expire / markNonceStale) so the AFTER_COMMIT termination event handler observes the
 * committed terminal state.
 *
 * <p>Replaces the bugfix/MOM-397 {@code ExecuteExecutionIntentTransactionBoundaryTest} which
 * exercised a TransactionTemplate-wrapper pattern that no longer exists in this branch (we use
 * {@code @Transactional(noRollbackFor = ExecutionIntentTerminalException.class)} on the delegate
 * instead). End-to-end runtime verification of the AFTER_COMMIT semantics is left to E2E tests.
 */
class TransactionalDelegateAnnotationTest {

  @Test
  @DisplayName("@Transactional(noRollbackFor = ExecutionIntentTerminalException.class) on delegate")
  void delegate_isTransactional_andDoesNotRollBackTerminalException() {
    Transactional annotation =
        TransactionalExecuteExecutionIntentDelegate.class.getAnnotation(Transactional.class);
    assertThat(annotation).as("@Transactional must be present on delegate").isNotNull();
    assertThat(annotation.noRollbackFor())
        .as(
            "noRollbackFor must include ExecutionIntentTerminalException to preserve MOM-397 intent")
        .contains(ExecutionIntentTerminalException.class);
  }
}
