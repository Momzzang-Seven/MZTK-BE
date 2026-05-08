package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;

/**
 * Transactional inner stage of {@link ExecuteInternalExecutionIntentUseCase}.
 *
 * <p>The orchestrator that implements {@code ExecuteInternalExecutionIntentUseCase} runs the
 * sponsor-wallet preflight (load + active + structural + KMS DescribeKey verify) OUTSIDE any
 * transaction, then hands the validated {@link SponsorWalletGate} to this port. Implementations
 * (and the bean-level {@code TransactionTemplate} wrapper) own the FOR UPDATE intent claim, nonce
 * reservation, sign, broadcast, and persistence steps that must be atomic.
 *
 * <p>Defined as a port to satisfy the architecture rule that application/service references the
 * delegate via interface injection, not via the concrete delegate class.
 */
public interface ExecuteTransactionalInternalExecutionIntentDelegatePort {

  ExecuteInternalExecutionIntentResult execute(
      ExecuteInternalExecutionIntentCommand command, SponsorWalletGate gate);
}
