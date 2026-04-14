package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.math.BigInteger;

public record PrepareTokenTransferPrevalidationResult(
    boolean ok,
    String failureReason,
    BigInteger gasLimit,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas) {}
