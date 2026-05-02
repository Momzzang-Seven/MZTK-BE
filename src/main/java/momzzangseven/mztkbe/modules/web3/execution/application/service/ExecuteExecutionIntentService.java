package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * Thin orchestrator for the user-facing execute-intent HTTP entry point.
 *
 * <p>Performs sponsor-wallet preflight (load + structural fail-fast + KMS DescribeKey verify)
 * BEFORE delegating to the {@link TransactionalExecuteExecutionIntentDelegate}. This ensures the
 * external KMS round-trip — which can stall under throttle / 5xx — never holds a JDBC connection
 * from the pool while the FOR UPDATE intent lock is open.
 *
 * <p>The delegate's {@code @Transactional} boundary covers the FOR UPDATE select + atomic write
 * semantics that prevent double-execute of the same intent.
 */
@Slf4j
@RequiredArgsConstructor
public class ExecuteExecutionIntentService implements ExecuteExecutionIntentUseCase {

  private static final String SPONSOR_WALLET_MISSING = "sponsor signer key is missing";

  private final TransactionalExecuteExecutionIntentDelegate delegate;
  private final LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;

  /**
   * Executes the target intent and returns latest intent/transaction summary.
   *
   * <p>Sponsor preflight runs unconditionally; the verify result is Caffeine-cached (60s) so the
   * warm-path cost is negligible. Behavior change vs the pre-refactor service: when the sponsor
   * wallet is missing/disabled, even already-submitted intents will now surface the "sponsor signer
   * key is missing" error here instead of returning the cached transaction. Race window between
   * preflight verify and the sign-time KMS call is bounded (~ms); KMS sign-time errors already map
   * to {@code KmsSignFailedException} retryable, so preflight staleness is acceptable.
   */
  @Override
  public ExecuteExecutionIntentResult execute(ExecuteExecutionIntentCommand command) {
    SponsorWalletGate gate = preflightSponsorWallet();
    return delegate.execute(command, gate);
  }

  /**
   * Loads sponsor wallet + applies structural fail-fast + invokes KMS DescribeKey verify.
   *
   * <p>Runs OUTSIDE any transaction so KMS latency cannot pin a JDBC connection. Throws {@link
   * Web3InvalidInputException} with the canonical {@code "sponsor signer key is missing"} message
   * for any structural defect (existing tests pin this message). Verify failures propagate as
   * {@code TreasuryWalletStateException}.
   */
  private SponsorWalletGate preflightSponsorWallet() {
    TreasuryWalletInfo walletInfo =
        loadSponsorTreasuryWalletPort
            .load()
            .orElseThrow(() -> new Web3InvalidInputException(SPONSOR_WALLET_MISSING));
    if (!walletInfo.active()) {
      throw new Web3InvalidInputException(SPONSOR_WALLET_MISSING);
    }
    if (walletInfo.kmsKeyId() == null || walletInfo.kmsKeyId().isBlank()) {
      throw new Web3InvalidInputException(SPONSOR_WALLET_MISSING);
    }
    if (walletInfo.walletAddress() == null || walletInfo.walletAddress().isBlank()) {
      throw new Web3InvalidInputException(SPONSOR_WALLET_MISSING);
    }
    // Validates EVM address shape; any malformed address throws Web3InvalidInputException.
    EvmAddress.of(walletInfo.walletAddress());
    // Pre-sign verification gate. Caffeine-cached (60s) inside the adapter, so warm-path is ~µs.
    verifyTreasuryWalletForSignPort.verify(walletInfo.walletAlias());
    TreasurySigner signer =
        new TreasurySigner(
            walletInfo.walletAlias(), walletInfo.kmsKeyId(), walletInfo.walletAddress());
    return new SponsorWalletGate(walletInfo, signer);
  }
}
