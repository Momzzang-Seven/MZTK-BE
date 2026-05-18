package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.service.CreateExecutionIntentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction boundary for shared execution intent creation.
 *
 * <p>The first insert for a root idempotency key cannot be protected by row locking because no row
 * exists yet. The database unique constraint on {@code (root_idempotency_key, attempt_no)} remains
 * the final guard; when a concurrent request wins the first insert, this wrapper retries once in a
 * fresh transaction so the delegate can re-read and reuse/reconcile the winning row.
 */
record TransactionalCreateExecutionIntentUseCase(
    CreateExecutionIntentService delegate, TransactionTemplate transactionTemplate)
    implements CreateExecutionIntentUseCase {

  @Override
  public CreateExecutionIntentResult execute(CreateExecutionIntentCommand command) {
    try {
      return executeOnce(command);
    } catch (DataIntegrityViolationException firstFailure) {
      return executeOnce(command);
    }
  }

  private CreateExecutionIntentResult executeOnce(CreateExecutionIntentCommand command) {
    return transactionTemplate.execute(status -> delegate.execute(command));
  }
}
