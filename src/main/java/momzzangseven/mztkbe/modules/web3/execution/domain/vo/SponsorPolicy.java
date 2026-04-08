package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import java.math.BigDecimal;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record SponsorPolicy(
    boolean enabled,
    long maxGasLimit,
    long maxMaxFeeGwei,
    long maxPriorityFeeGwei,
    BigDecimal perTxCapEth,
    BigDecimal perDayUserCapEth) {

  public SponsorPolicy {
    if (maxGasLimit < 21_000) {
      throw new Web3InvalidInputException("maxGasLimit must be >= 21000");
    }
    if (maxMaxFeeGwei <= 0) {
      throw new Web3InvalidInputException("maxMaxFeeGwei must be positive");
    }
    if (maxPriorityFeeGwei <= 0) {
      throw new Web3InvalidInputException("maxPriorityFeeGwei must be positive");
    }
    if (perTxCapEth == null || perTxCapEth.signum() < 0) {
      throw new Web3InvalidInputException("perTxCapEth must be >= 0");
    }
    if (perDayUserCapEth == null || perDayUserCapEth.signum() < 0) {
      throw new Web3InvalidInputException("perDayUserCapEth must be >= 0");
    }
  }
}
