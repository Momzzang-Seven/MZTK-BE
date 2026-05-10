package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;

/**
 * Diagnostic-grade probe for a treasury wallet's signing capability.
 *
 * <p>Returns a rich view ({@link ExecutionSignerCapabilityView}) carrying slot status, failure
 * reason, signer address, and signability — used by Spring Boot Actuator health indicators and
 * admin review surfaces that need to show <em>why</em> a wallet is or isn't signable.
 *
 * <p>For the throw-on-failure pre-sign gate used by signing pipelines, see {@code
 * VerifyTreasuryWalletForSignUseCase}. Both usecases compose the same out-ports ({@code
 * LoadTreasuryWalletPort} + {@code DescribeKmsKeyPort}) but expose different return semantics for
 * different consumer needs.
 */
public interface ProbeTreasuryWalletCapabilityUseCase {

  ExecutionSignerCapabilityView probe(String walletAlias);
}
