package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.math.BigInteger;

public record WalletUnsignedTxSnapshot(
    long chainId,
    String fromAddress,
    String toAddress,
    BigInteger value,
    String data,
    long nonce,
    BigInteger gasLimit,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas) {}
