package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;

/**
 * Thin orchestrator for the user-facing execute-intent HTTP entry point.
 *
 * <p>Performs sponsor-wallet preflight (load + structural fail-fast + KMS DescribeKey verify)
 * BEFORE delegating to {@link ExecuteTransactionalExecutionIntentDelegatePort}. This ensures the
 * external KMS round-trip — which can stall under throttle / 5xx — never holds a JDBC connection
 * from the pool while the FOR UPDATE intent lock is open. The delegate's {@code @Transactional}
 * boundary covers the FOR UPDATE select + atomic write semantics that prevent double-execute of the
 * same intent.
 *
 * <p>Polling fast-path: clients commonly re-call this endpoint to check the on-chain status of an
 * already-submitted intent. We peek at the intent (non-locking) before preflight; if {@code
 * submittedTxId != null}, the broadcast already happened and there is nothing for sponsor preflight
 * to gate. Returning the cached transaction summary directly means an INACTIVE wallet later in time
 * does not surface as a 400 on a successful past transaction.
 */
@Slf4j
@RequiredArgsConstructor
public class ExecuteExecutionIntentService implements ExecuteExecutionIntentUseCase {

  private final ExecuteTransactionalExecutionIntentDelegatePort delegate;
  private final SponsorWalletPreflight sponsorWalletPreflight;
  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final ExecutionTransactionGatewayPort executionTransactionGatewayPort;

  /**
   * Executes the target intent and returns latest intent/transaction summary.
   *
   * <p>Polling fast-path runs first — already-submitted intents return their cached transaction
   * snapshot without invoking sponsor preflight. Otherwise sponsor preflight runs (verify result is
   * Caffeine-cached 60s, so warm-path cost is negligible) and the locked, atomic execute proceeds
   * inside the transactional delegate.
   */
  @Override
  public ExecuteExecutionIntentResult execute(ExecuteExecutionIntentCommand command) {
    Optional<ExecuteExecutionIntentResult> polling = tryPollingFastPath(command);
    if (polling.isPresent()) {
      return polling.get();
    }
    SponsorWalletGate gate = sponsorWalletPreflight.preflight();
    return delegate.execute(command, gate);
  }

  private Optional<ExecuteExecutionIntentResult> tryPollingFastPath(
      ExecuteExecutionIntentCommand command) {
    ExecutionIntent intent =
        executionIntentPersistencePort.findByPublicId(command.executionIntentId()).orElse(null);
    if (intent == null || intent.getSubmittedTxId() == null) {
      return Optional.empty();
    }
    if (!intent.getRequesterUserId().equals(command.requesterUserId())) {
      throw new Web3InvalidInputException("execution intent owner mismatch");
    }
    ExecutionTransactionGatewayPort.TransactionRecord transaction =
        executionTransactionGatewayPort
            .findById(intent.getSubmittedTxId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "transaction not found: " + intent.getSubmittedTxId()));
    return Optional.of(
        new ExecuteExecutionIntentResult(
            intent.getPublicId(),
            intent.getStatus(),
            transaction.transactionId(),
            transaction.status(),
            transaction.txHash()));
  }
}
