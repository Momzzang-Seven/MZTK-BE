package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.math.BigDecimal;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Wallet-owned view of the EIP-7702 sponsor policy required for approval capability checks. */
public record WalletApprovalSponsorPolicy(
    boolean enabled,
    long maxGasLimit,
    long maxMaxFeeGwei,
    BigDecimal perTxCapEth,
    BigDecimal perDayUserCapEth) {

  public WalletApprovalSponsorPolicy {
    if (maxGasLimit < 21_000) {
      throw new Web3InvalidInputException("maxGasLimit must be >= 21000");
    }
    if (maxMaxFeeGwei <= 0) {
      throw new Web3InvalidInputException("maxMaxFeeGwei must be positive");
    }
    if (perTxCapEth == null || perTxCapEth.signum() < 0) {
      throw new Web3InvalidInputException("perTxCapEth must be >= 0");
    }
    if (perDayUserCapEth == null || perDayUserCapEth.signum() < 0) {
      throw new Web3InvalidInputException("perDayUserCapEth must be >= 0");
    }
  }
}
