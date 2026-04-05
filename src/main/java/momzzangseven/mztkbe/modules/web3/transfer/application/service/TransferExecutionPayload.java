package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.math.BigInteger;

public record TransferExecutionPayload(
    String clientRequestId,
    Long fromUserId,
    Long toUserId,
    String authorityAddress,
    String toAddress,
    String tokenContractAddress,
    BigInteger amountWei) {}
