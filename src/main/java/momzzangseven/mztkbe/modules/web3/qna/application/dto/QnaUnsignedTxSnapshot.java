package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.math.BigInteger;

public record QnaUnsignedTxSnapshot(
    long chainId,
    String fromAddress,
    String toAddress,
    BigInteger value,
    String data,
    long nonce,
    BigInteger gasLimit,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas) {}
