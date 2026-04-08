package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import java.math.BigInteger;
import java.util.List;

public record Eip7702ExecutionSignCommand(
    long chainId,
    BigInteger nonce,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas,
    BigInteger gasLimit,
    String to,
    BigInteger value,
    String data,
    List<Eip7702ExecutionAuthorizationTuple> authorizationList,
    String sponsorPrivateKeyHex) {}
