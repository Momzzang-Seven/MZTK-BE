package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

/**
 * Execution-side bridging port for the pre-sign treasury verification gate. Implemented by an
 * adapter under {@code infrastructure/external/treasury/} that delegates to the treasury module's
 * {@code VerifyTreasuryWalletForSignUseCase}; execution-layer callers never import treasury types
 * directly.
 */
public interface VerifyTreasuryWalletForSignPort {

  /**
   * Verify that the wallet bound to {@code walletAlias} is signable.
   *
   * @param walletAlias canonical alias of the wallet about to sign
   * @throws momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException if the wallet
   *     is missing, not ACTIVE, or its KMS key is not ENABLED
   */
  void verify(String walletAlias);
}
