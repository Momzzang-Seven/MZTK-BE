package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.math.BigInteger;

public record PrepareTokenTransferPrevalidationCommand(
    String fromAddress, String toAddress, BigInteger amountWei) {}
