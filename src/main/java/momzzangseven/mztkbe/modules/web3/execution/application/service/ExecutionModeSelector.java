package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;

@RequiredArgsConstructor
/**
 * Selects execution mode for a newly created intent.
 *
 * <p>The selector evaluates sponsor policy and current user exposure to decide whether EIP-7702 is
 * eligible, otherwise falls back to EIP-1559 when draft fallback is allowed.
 */
public class ExecutionModeSelector {

  private static final BigInteger WEI_SCALE = BigInteger.TEN.pow(18);
  private static final BigInteger WEI_PER_GWEI = BigInteger.valueOf(1_000_000_000L);
  private static final BigInteger RESERVATION_NUMERATOR = BigInteger.valueOf(12);
  private static final BigInteger RESERVATION_DENOMINATOR = BigInteger.TEN;

  private final LoadSponsorPolicyPort loadSponsorPolicyPort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final Clock appClock;

  /** Returns selected mode and reservation metadata used during intent creation. */
  public ExecutionModeSelection select(CreateExecutionIntentCommand command) {
    LocalDate usageDateKst = LocalDate.now(appClock);
    if (requiresDirectEip1559(command)) {
      return new ExecutionModeSelection(ExecutionMode.EIP1559, BigInteger.ZERO, usageDateKst);
    }

    SponsorPolicy sponsorPolicy = loadSponsorPolicyPort.loadSponsorPolicy();
    BigInteger reservedCostWei = estimateReservedCostWei(sponsorPolicy);
    if (isSponsorEligible(command, sponsorPolicy, reservedCostWei, usageDateKst)) {
      return new ExecutionModeSelection(ExecutionMode.EIP7702, reservedCostWei, usageDateKst);
    }

    if (command.draft().fallbackAllowed() && command.draft().unsignedTxSnapshot() != null) {
      return new ExecutionModeSelection(ExecutionMode.EIP1559, BigInteger.ZERO, usageDateKst);
    }

    throw new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true);
  }

  private boolean requiresDirectEip1559(CreateExecutionIntentCommand command) {
    return command.draft().unsignedTxSnapshot() != null
        && command.draft().authorityAddress() == null
        && command.draft().authorityNonce() == null
        && command.draft().delegateTarget() == null
        && command.draft().authorizationPayloadHash() == null
        && (command.draft().actionType() == ExecutionActionTypeCode.QNA_ADMIN_SETTLE
            || command.draft().actionType() == ExecutionActionTypeCode.QNA_ADMIN_REFUND);
  }

  private boolean isSponsorEligible(
      CreateExecutionIntentCommand command,
      SponsorPolicy sponsorPolicy,
      BigInteger reservedCostWei,
      LocalDate usageDateKst) {
    if (!sponsorPolicy.enabled()) {
      return false;
    }
    if (command.draft().authorityAddress() == null
        || command.draft().authorityNonce() == null
        || command.draft().delegateTarget() == null
        || command.draft().authorizationPayloadHash() == null) {
      return false;
    }
    if (reservedCostWei.compareTo(ethToWei(sponsorPolicy.perTxCapEth())) > 0) {
      return false;
    }

    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort
            .find(command.draft().requesterUserId(), usageDateKst)
            .orElseGet(
                () -> SponsorDailyUsage.create(command.draft().requesterUserId(), usageDateKst));

    return usage
            .totalExposureWei()
            .add(reservedCostWei)
            .compareTo(ethToWei(sponsorPolicy.perDayUserCapEth()))
        <= 0;
  }

  private BigInteger ethToWei(BigDecimal eth) {
    return eth.multiply(new BigDecimal(WEI_SCALE)).toBigIntegerExact();
  }

  private BigInteger estimateReservedCostWei(SponsorPolicy sponsorPolicy) {
    BigInteger gasLimit = BigInteger.valueOf(sponsorPolicy.maxGasLimit());
    BigInteger maxFeePerGas =
        BigInteger.valueOf(sponsorPolicy.maxMaxFeeGwei()).multiply(WEI_PER_GWEI);
    BigInteger base = gasLimit.multiply(maxFeePerGas);
    return base.multiply(RESERVATION_NUMERATOR).divide(RESERVATION_DENOMINATOR);
  }

  /** Immutable selection result for mode and sponsor reservation context. */
  public record ExecutionModeSelection(
      ExecutionMode mode, BigInteger reservedSponsorCostWei, LocalDate sponsorUsageDateKst) {}
}
