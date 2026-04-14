package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import java.math.BigInteger;

public record PrepareTokenTransferExecutionSupportCommand(
    long chainId,
    String delegateTarget,
    String authorityAddress,
    String toAddress,
    BigInteger amountWei) {}
