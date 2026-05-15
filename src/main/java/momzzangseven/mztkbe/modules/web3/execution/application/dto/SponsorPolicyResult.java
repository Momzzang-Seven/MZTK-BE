package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.math.BigDecimal;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;

public record SponsorPolicyResult(
    boolean enabled,
    long maxGasLimit,
    long maxMaxFeeGwei,
    long maxPriorityFeeGwei,
    BigDecimal perTxCapEth,
    BigDecimal perDayUserCapEth) {

  public static SponsorPolicyResult from(SponsorPolicy policy) {
    return new SponsorPolicyResult(
        policy.enabled(),
        policy.maxGasLimit(),
        policy.maxMaxFeeGwei(),
        policy.maxPriorityFeeGwei(),
        policy.perTxCapEth(),
        policy.perDayUserCapEth());
  }
}
