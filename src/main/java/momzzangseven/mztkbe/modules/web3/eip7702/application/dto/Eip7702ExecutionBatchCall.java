package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import java.math.BigInteger;

public record Eip7702ExecutionBatchCall(String to, BigInteger value, byte[] data) {}
