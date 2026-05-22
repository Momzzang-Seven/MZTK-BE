package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionSignerGates;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalInternalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.InternalExecutionSignerPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.application.util.KmsClientErrorClassifier;

/**
 * Thin orchestrator for the cron-driven internal execution-intent path.
 *
 * <p>Mirrors the external {@code ExecuteExecutionIntentService} ↔ {@code
 * TransactionalExecuteExecutionIntentDelegate} split established by MOM-340 commit {@code
 * 5e973494}: signer-wallet preflight (load + active + structural + KMS DescribeKey verify) runs
 * OUTSIDE any transaction so KMS round-trip latency cannot pin a JDBC connection while a FOR UPDATE
 * intent lock is held by the delegate. Only after preflight succeeds does this orchestrator hand
 * the validated signer gates to the transactional delegate.
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
  private final InternalExecutionSignerPreflight internalExecutionSignerPreflight;
  private final ExecutionIntentPersistencePort executionIntentPersistencePort;

  @Override
  public ExecuteInternalExecutionIntentResult execute(
      ExecuteInternalExecutionIntentCommand command) {
    // No-work short-circuit: skip sponsor preflight (and the terminal-KMS ERROR log it can produce)
    // when the queue has nothing to claim. Without this, a broken sponsor wallet would emit one
    // INTERNAL_EXECUTION_PREFLIGHT_TERMINAL_KMS line per scheduler tick even with zero pending
    // intents, drowning the alert signal in noise.
    if (!executionIntentPersistencePort.existsClaimableInternal(command.actionTypes())) {
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    }
    InternalExecutionSignerGates signerGates = preflightAvailableSigners(command.actionTypes());
    if (signerGates == null) {
      return ExecuteInternalExecutionIntentResult.preflightSkipped();
    }
    ExecuteInternalExecutionIntentCommand executableCommand =
        new ExecuteInternalExecutionIntentCommand(new ArrayList<>(signerGates.gates().keySet()));
    return delegate.execute(executableCommand, signerGates);
  }

  private InternalExecutionSignerGates preflightAvailableSigners(
      List<ExecutionActionType> actionTypes) {
    Map<ExecutionActionType, SponsorWalletGate> gates = new EnumMap<>(ExecutionActionType.class);
    for (ExecutionActionType actionType : actionTypes) {
      try {
        gates.putAll(internalExecutionSignerPreflight.preflight(List.of(actionType)).gates());
      } catch (Web3InvalidInputException e) {
        logSkip(actionType, "WALLET_INVALID", e);
      } catch (TreasuryWalletStateException e) {
        logSkip(actionType, "WALLET_STATE", e);
      } catch (KmsKeyDescribeFailedException e) {
        if (KmsClientErrorClassifier.isTerminal(e)) {
          logTerminal(actionType, "KMS_DESCRIBE_TERMINAL", e);
        } else {
          logSkip(actionType, "KMS_DESCRIBE_FAILED", e);
        }
      }
    }
    return gates.isEmpty() ? null : new InternalExecutionSignerGates(gates);
  }

  /**
   * Emit a structured WARN line that operators can grep on to find intents stuck in
   * AWAITING_SIGNATURE because sponsor preflight is repeatedly failing. The {@code
   * event=INTERNAL_EXECUTION_PREFLIGHT_SKIPPED} tag is the agreed search anchor — pair it with a
   * {@code SELECT * FROM web3_execution_intents WHERE status='AWAITING_SIGNATURE'} query to find
   * the affected rows.
   */
  private void logSkip(ExecutionActionType actionType, String reason, RuntimeException e) {
    log.warn(
        "event=INTERNAL_EXECUTION_PREFLIGHT_SKIPPED actionType={} reason={} exception={} message={}",
        actionType,
        reason,
        e.getClass().getSimpleName(),
        e.getMessage());
  }

  /**
   * Emit an ERROR line distinct from the transient skip log so alert rules can trigger on terminal
   * KMS configuration errors (IAM deny, key not found, key disabled) that will not self-heal — pair
   * with the same {@code AWAITING_SIGNATURE} query to identify affected intents.
   */
  private void logTerminal(ExecutionActionType actionType, String reason, RuntimeException e) {
    log.error(
        "event=INTERNAL_EXECUTION_PREFLIGHT_TERMINAL_KMS actionType={} reason={} exception={} message={}",
        actionType,
        reason,
        e.getClass().getSimpleName(),
        e.getMessage());
  }
}
