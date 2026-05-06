package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalInternalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.shared.application.util.KmsClientErrorClassifier;

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
      logSkip("WALLET_INVALID", e);
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    } catch (TreasuryWalletStateException e) {
      logSkip("WALLET_STATE", e);
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    } catch (KmsKeyDescribeFailedException e) {
      // Terminal AWS KMS DescribeKey failures (AccessDenied / NotFound / Disabled / ...) are
      // logged at ERROR with a distinct event tag so operators can alert on them — without
      // surfacing as a crash or cascading any intent-level event. Intent claim is still skipped
      // because the sponsor wallet itself is broken; no specific intent is at fault.
      if (KmsClientErrorClassifier.isTerminal(e)) {
        logTerminal("KMS_DESCRIBE_TERMINAL", e);
      } else {
        logSkip("KMS_DESCRIBE_FAILED", e);
      }
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    }
    return delegate.execute(command, gate);
  }

  /**
   * Emit a structured WARN line that operators can grep on to find intents stuck in
   * AWAITING_SIGNATURE because sponsor preflight is repeatedly failing. The {@code
   * event=INTERNAL_EXECUTION_PREFLIGHT_SKIPPED} tag is the agreed search anchor — pair it with a
   * {@code SELECT * FROM web3_execution_intents WHERE status='AWAITING_SIGNATURE'} query to find
   * the affected rows.
   */
  private void logSkip(String reason, RuntimeException e) {
    log.warn(
        "event=INTERNAL_EXECUTION_PREFLIGHT_SKIPPED reason={} exception={} message={}",
        reason,
        e.getClass().getSimpleName(),
        e.getMessage());
  }

  /**
   * Emit an ERROR line distinct from the transient skip log so alert rules can trigger on terminal
   * KMS configuration errors (IAM deny, key not found, key disabled) that will not self-heal — pair
   * with the same {@code AWAITING_SIGNATURE} query to identify affected intents.
   */
  private void logTerminal(String reason, RuntimeException e) {
    log.error(
        "event=INTERNAL_EXECUTION_PREFLIGHT_TERMINAL_KMS reason={} exception={} message={}",
        reason,
        e.getClass().getSimpleName(),
        e.getMessage());
  }
}
