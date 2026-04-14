package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;

public record TransferExecutionDraftCall(String target, BigInteger value, String data) {}
