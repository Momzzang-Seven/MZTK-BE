package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

/**
 * Pre-sign gate invoked immediately before a caller delegates to {@code SignDigestPort}. Two checks
 * run in sequence:
 *
 * <ol>
 *   <li>{@code TreasuryWallet.assertSignable()} — wallet must be {@code ACTIVE}.
 *   <li>{@code DescribeKmsKeyPort.describe(kmsKeyId) == ENABLED} — the backing KMS key must be live.
 * </ol>
 *
 * <p>Either failure throws a domain exception so the caller can record the audit and abort before
 * incurring a KMS Sign call.
 */
public interface VerifyTreasuryWalletForSignUseCase {

  /**
   * @param walletAlias canonical alias of the wallet about to sign
   * @throws momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException if the wallet
   *     is missing, not ACTIVE, or its KMS key is not ENABLED
   */
  void execute(String walletAlias);
}
