package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.shared.application.util.KmsClientErrorClassifier;

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
 * <p>The non-locking peek runs once and feeds two short-circuits:
 *
 * <ol>
 *   <li><b>Polling fast-path</b> — clients re-call this endpoint to check the on-chain status of an
 *       already-submitted intent. If {@code submittedTxId != null}, return the cached transaction
 *       summary so an INACTIVE wallet later in time does not surface as 400 on a past success.
 *   <li><b>EIP-1559 short-circuit</b> — EIP-1559 intents carry a user-signed raw transaction and
 *       never consume sponsor wallet material. Run delegate without preflight so a sponsor outage
 *       cannot block a legitimate user retry. {@code gate=null} is passed to the delegate; the
 *       EIP-7702 branch enforces non-null on entry.
 * </ol>
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
   * <p>Single non-locking peek decides the path: cached snapshot → return; pending EIP-1559 →
   * delegate with no preflight; everything else → preflight then delegate. Owner mismatch on the
   * cached path is enforced here; on the new-intent path it is enforced by the delegate's FOR
   * UPDATE select.
   */
  @Override
  public ExecuteExecutionIntentResult execute(ExecuteExecutionIntentCommand command) {
    ExecutionIntent peeked =
        executionIntentPersistencePort.findByPublicId(command.executionIntentId()).orElse(null);

    Optional<ExecuteExecutionIntentResult> cached = tryPollingFastPath(command, peeked);
    if (cached.isPresent()) {
      return cached.get();
    }

    if (peeked != null && peeked.getMode() == ExecutionMode.EIP1559) {
      return delegate.execute(command, null);
    }

    SponsorWalletGate gate = preflightOrTranslate();
    return delegate.execute(command, gate);
  }

  /**
   * Runs sponsor-wallet preflight and translates {@link KmsKeyDescribeFailedException} into a
   * {@link Web3TransferException} carrying a retryable signal so HTTP clients can distinguish
   * transient AWS hiccups (retry safe) from terminal config errors (operator action required).
   * Other preflight exceptions ({@code Web3InvalidInputException}, {@code
   * TreasuryWalletStateException}) propagate unchanged to their existing handlers.
   */
  private SponsorWalletGate preflightOrTranslate() {
    try {
      return sponsorWalletPreflight.preflight();
    } catch (KmsKeyDescribeFailedException e) {
      boolean terminal = KmsClientErrorClassifier.isTerminal(e);
      if (terminal) {
        log.error(
            "event=USER_EXECUTION_PREFLIGHT_TERMINAL_KMS exception={} message={}",
            e.getClass().getSimpleName(),
            e.getMessage());
      } else {
        log.warn(
            "event=USER_EXECUTION_PREFLIGHT_TRANSIENT_KMS exception={} message={}",
            e.getClass().getSimpleName(),
            e.getMessage());
      }
      throw new Web3TransferException(
          ErrorCode.WEB3_KMS_KEY_DESCRIBE_FAILED, e.getMessage(), e, !terminal);
    }
  }

  private Optional<ExecuteExecutionIntentResult> tryPollingFastPath(
      ExecuteExecutionIntentCommand command, ExecutionIntent intent) {
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
