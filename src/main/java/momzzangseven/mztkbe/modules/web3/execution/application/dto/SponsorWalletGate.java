package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

/**
 * Carrier for sponsor-wallet preflight output.
 *
 * <p>Produced outside the transactional execute boundary so KMS DescribeKey latency cannot pin a
 * JDBC connection during the FOR UPDATE intent lock. Holds the wallet snapshot used for
 * post-signing audit fields and the {@link TreasurySigner} capability handle reused by the EIP-7702
 * / EIP-1559 sponsor signing paths.
 *
 * @param walletInfo execution-local wallet projection (already structurally validated)
 * @param signer KMS-backed signer handle (already verified via DescribeKey)
 */
public record SponsorWalletGate(TreasuryWalletInfo walletInfo, TreasurySigner signer) {

  /** Compact constructor — both components are required for downstream signing. */
  public SponsorWalletGate {
    if (walletInfo == null) {
      throw new IllegalArgumentException("walletInfo required");
    }
    if (signer == null) {
      throw new IllegalArgumentException("signer required");
    }
  }
}
