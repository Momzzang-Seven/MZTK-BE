package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionTransactionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Structural test asserting that {@link TransactionalExecuteExecutionIntentDelegate} preserves the
 * MOM-458 transaction-boundary intent: sponsor KMS signing must not run under a class-level
 * transaction that holds intent/sponsor nonce locks.
 *
 * <p>Runtime verification of the terminal-state commit behavior lives in {@link
 * TransactionalExecuteExecutionIntentDelegateBoundaryTest}. This test guards the structural
 * contract: the delegate owns an explicit short transaction port and must not be wrapped by a
 * whole-method {@link Transactional} boundary again.
 */
class TransactionalDelegateAnnotationTest {

  @Test
  @DisplayName("delegate uses explicit short transaction port, not class-level @Transactional")
  void delegate_usesExplicitTransactionPortInsteadOfClassLevelTransaction() {
    Transactional annotation =
        TransactionalExecuteExecutionIntentDelegate.class.getAnnotation(Transactional.class);
    assertThat(annotation)
        .as("class-level @Transactional would hold locks across sponsor signing")
        .isNull();
    assertThat(TransactionalExecuteExecutionIntentDelegate.class.getDeclaredFields())
        .anySatisfy(
            field -> assertThat(field.getType()).isEqualTo(RunExecutionTransactionPort.class));
  }
}
