package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;

/**
 * Transactional inner stage of {@link ExecuteExecutionIntentUseCase}.
 *
 * <p>The orchestrator that implements {@code ExecuteExecutionIntentUseCase} runs the sponsor-wallet
 * preflight (load + active + structural + KMS DescribeKey verify) OUTSIDE any transaction, then
 * hands the validated {@link SponsorWalletGate} to this port. The implementing delegate owns the
 * FOR UPDATE intent claim, sign, broadcast, and persistence steps that must be atomic.
 *
 * <p>Defined as a port to satisfy the architecture rule that application/service references sibling
 * delegates via interface injection, not via the concrete delegate class.
 */
public interface ExecuteTransactionalExecutionIntentDelegatePort {

  ExecuteExecutionIntentResult execute(
      ExecuteExecutionIntentCommand command, SponsorWalletGate gate);
}
