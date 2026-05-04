package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalInternalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;

/**
 * Thin orchestrator for the cron-driven internal execution-intent path.
 *
 * <p>Mirrors the external {@code ExecuteExecutionIntentService} ↔ {@code
 * TransactionalExecuteExecutionIntentDelegate} split established by MOM-340 commit {@code
 * 5e973494}: sponsor-wallet preflight (load + active + structural + KMS DescribeKey verify) runs
 * OUTSIDE any transaction so KMS round-trip latency cannot pin a JDBC connection while a FOR UPDATE
 * intent lock is held by the delegate. Only after preflight succeeds does this orchestrator hand
 * the validated {@link SponsorWalletGate} to the transactional delegate.
 *
 * <p>On preflight failure, returns {@link ExecuteInternalExecutionIntentResult#preflightSkipped()}
 * without claiming an intent — no {@code ExecutionIntentTerminatedEvent} is published, no QnA
 * escrow refund cascade fires. The cron batch loop sees {@code executed=false} and exits the tick.
 */
@Slf4j
@RequiredArgsConstructor
public class ExecuteInternalExecutionIntentService
    implements ExecuteInternalExecutionIntentUseCase {

  private final ExecuteTransactionalInternalExecutionIntentDelegatePort delegate;
  private final SponsorWalletPreflight sponsorWalletPreflight;

  @Override
  public ExecuteInternalExecutionIntentResult execute(
      ExecuteInternalExecutionIntentCommand command) {
    SponsorWalletGate gate;
    try {
      gate = sponsorWalletPreflight.preflight();
    } catch (Web3InvalidInputException e) {
      log.warn("internal execution preflight skipped: {}", e.getMessage());
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    } catch (TreasuryWalletStateException e) {
      log.warn("internal execution preflight skipped due to wallet state: {}", e.getMessage(), e);
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    }
    return delegate.execute(command, gate);
  }
}
