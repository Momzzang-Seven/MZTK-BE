package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import java.math.BigInteger;

public record Eip7702ExecutionAuthorizationTuple(
    BigInteger chainId,
    String address,
    BigInteger nonce,
    BigInteger yParity,
    BigInteger r,
    BigInteger s) {}
