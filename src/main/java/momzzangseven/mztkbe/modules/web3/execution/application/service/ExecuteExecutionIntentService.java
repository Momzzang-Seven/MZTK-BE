package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;

/**
 * Thin orchestrator for the user-facing execute-intent HTTP entry point.
 *
 * <p>Performs sponsor-wallet preflight (load + structural fail-fast + KMS DescribeKey verify)
 * BEFORE delegating to {@link ExecuteTransactionalExecutionIntentDelegatePort}. This ensures the
 * external KMS round-trip — which can stall under throttle / 5xx — never holds a JDBC connection
 * from the pool while the FOR UPDATE intent lock is open. The delegate's {@code @Transactional}
 * boundary covers the FOR UPDATE select + atomic write semantics that prevent double-execute of the
 * same intent.
 */
@Slf4j
@RequiredArgsConstructor
public class ExecuteExecutionIntentService implements ExecuteExecutionIntentUseCase {

  private final ExecuteTransactionalExecutionIntentDelegatePort delegate;
  private final SponsorWalletPreflight sponsorWalletPreflight;

  /**
   * Executes the target intent and returns latest intent/transaction summary.
   *
   * <p>Sponsor preflight runs unconditionally; the verify result is Caffeine-cached (60s) so the
   * warm-path cost is negligible. Behavior change vs the pre-refactor service: when the sponsor
   * wallet is missing/disabled, even already-submitted intents will surface the "sponsor signer key
   * is missing" error here instead of returning the cached transaction. Race window between
   * preflight verify and the sign-time KMS call is bounded (~ms); KMS sign-time errors already map
   * to {@code KmsSignFailedException} retryable, so preflight staleness is acceptable.
   */
  @Override
  public ExecuteExecutionIntentResult execute(ExecuteExecutionIntentCommand command) {
    SponsorWalletGate gate = sponsorWalletPreflight.preflight();
    return delegate.execute(command, gate);
  }
}
