package momzzangseven.mztkbe.modules.web3.execution.application.util;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * Sponsor-wallet preflight shared by external (user-facing) and internal (cron) execute paths.
 *
 * <p>Runs OUTSIDE any transactional boundary: KMS DescribeKey latency must never pin a JDBC
 * connection while a FOR UPDATE intent lock is held. The verify call is Caffeine-cached (60s)
 * inside the adapter, so the warm-path cost is ~µs.
 *
 * <p>Throws {@link Web3InvalidInputException} for any structural defect (missing wallet, inactive,
 * blank kms key id, blank/malformed wallet address). The external path lets this propagate to the
 * HTTP layer; the internal orchestrator catches it and reports {@code preflightSkipped()} so the
 * cron batch loop exits cleanly without claiming an intent.
 */
@RequiredArgsConstructor
public class SponsorWalletPreflight {

  private static final String SPONSOR_WALLET_MISSING = "sponsor signer key is missing";

  private final LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;

  public SponsorWalletGate preflight() {
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
    EvmAddress.of(walletInfo.walletAddress());
    verifyTreasuryWalletForSignPort.verify(walletInfo.walletAlias());
    TreasurySigner signer =
        new TreasurySigner(
            walletInfo.walletAlias(), walletInfo.kmsKeyId(), walletInfo.walletAddress());
    return new SponsorWalletGate(walletInfo, signer);
  }
}
